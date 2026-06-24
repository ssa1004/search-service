# ADR-0011: Spring Boot graceful shutdown + K8s preStop

## 상태
적용

## 배경
SIGTERM 즉시 종료 시:
- in-flight HTTP 요청이 502/connection reset — caller 측 retry 가 정상이라도 사용자 입장에서
  실패율 spike 의 원인.
- CDC consumer 의 message processing 이 끊겨 같은 offset 재처리 (idempotent 라 결과는 같지만
  로그 / 메트릭 noise).

K8s rollout / scale-in 마다 매번 발생하는 정상 흐름이라 흡수해야 한다.

## 결정
**Spring Boot graceful shutdown + K8s preStop sleep + lifecycle timeout 의 3단 예산.**

| 단계 | 시간 | 역할 |
| --- | --- | --- |
| `terminationGracePeriodSeconds` | 30s | 전체 예산 (K8s default 와 일치). |
| `preStop sleep` | 5s | K8s endpoint 갱신 → kube-proxy 전파 대기. 새 connection 이 본 pod 으로 안 옴. |
| `server.shutdown=graceful` | (Spring) | tomcat connector 가 새 요청 거부, in-flight 응답까지 대기. |
| `spring.lifecycle.timeout-per-shutdown-phase` | 25s | SmartLifecycle 빈 (Kafka container 등) phase 당 25s drain. |
| 잔여 강제 kill | 0s | 30s 초과 시 SIGKILL. |

5s + 25s = 30s 안에 모든 정리. preStop 5s 는 endpoint 전파 시간의 통상치 (cluster 별 측정 후 조정).

## 장단점
- 장점: rollout / HPA scale-in 시 502 사라짐 — caller retry 부담 ↓.
- 장점: Kafka consumer 의 partial commit 으로 인한 중복 처리 ↓.
- 단점: 모든 rollout 이 5-30s 더 걸림 — emergency rollback 시에는 답답할 수 있음 (--grace-period=0
  로 강제 가능).
- 단점: 25s 안에 끝나지 않는 long-running endpoint (예: bulk reindex trigger) 는 강제 kill —
  이런 endpoint 는 async + status polling 패턴이어야 한다.

## 용어 풀이 (쉽게)

- **graceful shutdown(우아한 종료)** — 서버를 끌 때 새 요청은 안 받되 처리 중인 요청은 끝까지 마치고 종료. 식당 마감 때 신규 손님은 안 받고 안에 계신 분 식사는 끝내드리는 것.
- **SIGTERM** — 운영체제가 "이제 그만 종료해"라고 프로세스에 보내는 정중한 종료 신호. 무시하고 버티면 잠시 뒤 강제 종료(SIGKILL)된다.
- **in-flight 요청** — 지금 한창 처리 중인, 아직 응답이 안 나간 요청들.
- **preStop sleep** — pod를 끄기 직전 잠깐 멈춰, "이 pod엔 새 손님 보내지 마"라는 안내가 전체에 퍼질 시간을 버는 단계.
- **drain(드레인)** — 새 일은 그만 받고 남은 일만 비워 내며 마무리하는 것. 수도꼭지를 잠그고 남은 물만 빼는 셈.
- **rollout / HPA scale-in** — 새 버전 배포(rollout)나 트래픽 줄 때 pod 수를 자동으로 줄이는 것(scale-in). 둘 다 평소에 자주 일어나는 종료라 매끄럽게 흡수해야 한다.

## 다시 검토할 시점
- preStop 의 5s 가 부족 (502 잔존) 또는 과잉 (rollout 너무 느림) 인지 endpoint 전파 측정 후
  조정.
- cluster 가 service mesh (Istio 등) 도입 시 sidecar drain 순서까지 고려 — preStop 늘리거나
  sidecar 재구성.
