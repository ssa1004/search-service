# ADR-0010: K8s 3종 probe + ES 종속 readiness coordinator

## 상태
적용

## 배경
K8s probe 를 readiness + liveness 두 개만 운영하면 두 가지 문제가 자주 생긴다:

- 부팅 (Flyway 마이그레이션 + ES mapping init) 이 느릴 때 readiness 가 fail → restart loop.
  initialDelaySeconds 를 무조건 크게 잡으면 정상 시점에는 헛 기다림.
- ES cluster 가 일시 unavailable 일 때 검색 endpoint 가 cascade fail (5xx) — caller 측은
  retry storm. K8s 가 traffic 을 떼어낼 신호가 필요하다.

검색 서비스 도메인 특성:
- ES 는 사실상 핵심 dependency — ES DOWN 이면 검색 endpoint 의 대부분이 무의미.
- Kafka 는 부수적이다 — CDC consumer 만 영향 받고 본인이 retry. REST 검색은 Kafka 와 무관.

## 결정
**3종 probe + 도메인 헬스 indicator + readiness coordinator.**

### Probe 구성
| probe | path | 의미 |
| --- | --- | --- |
| `startupProbe` | `/actuator/health/liveness` | 부팅 완료 전까지 다른 probe 를 막는다. failureThreshold 30 * periodSeconds 5 = 150s 까지 부팅 허용. |
| `readinessProbe` | `/actuator/health/readiness` | ES ping 결과 + Spring readiness state. DOWN → traffic 차단. |
| `livenessProbe` | `/actuator/health/liveness` | 회복 불가 상태 (deadlock / OOM 직전) 만. ES 일시 불가는 영향 없음. |

### Readiness 그룹
`management.endpoint.health.group.readiness.include = readinessState, searchEngine`.
`searchEngine` 은 `SearchEngineHealthIndicator` 가 ES `ping()` 결과 직접 보고.

### ApplicationReadinessCoordinator
5초마다 ES ping sampling →
- 연속 3회 fail 시 `AvailabilityChangeEvent.publish(REFUSING_TRAFFIC)`. flap 방어.
- 1회 UP 회복 시 즉시 `ACCEPTING_TRAFFIC` — 회복은 빠르게.
- Kafka 는 sampling 대상 아님 — CDC consumer 가 자체 retry.

게이지 `application.readiness.accepting` (1=accepting, 0=refusing) 노출.

## 장단점
- 장점: cascade fail 차단 — ES 죽으면 K8s LB 가 traffic 즉시 떼어내 client 가 다른 cluster /
  fallback 으로 routing 가능.
- 장점: startup 과 liveness 분리 — 부팅 느린 시점에 liveness 로 잘못 죽이는 사고 방지.
- 단점: ES ping 자체가 부하. 5s 주기는 cluster 부하에 영향 미미하지만 관찰 필요.
- 단점: ES 전체 fail 이 아니라 일부 shard 만 red 인 경우는 ping 으로 못 잡는다 — 별도 search
  성공률 메트릭 + alert 가 보완.

## 용어 풀이 (쉽게)

- **K8s 3종 probe** — 쿠버네티스가 컨테이너 상태를 보는 세 검사. startup(부팅 다 됐나?), readiness(손님 받아도 되나?), liveness(살아는 있나?).
- **liveness에 외부 의존 제외** — '살아 있나' 검사에는 DB·ES를 넣지 않는다. ES가 잠깐 흔들렸다고 멀쩡한 pod까지 재시작되는 걸 막으려고.
- **cascade fail(연쇄 장애)** — ES가 죽으면 그걸 부르는 검색이 줄줄이 5xx로 무너지고, 그 위로 도미노처럼 번지는 장애.
- **retry storm(재시도 폭주)** — 실패한 호출을 모두가 동시에 다시 때려, 가뜩이나 힘든 서버를 더 죽이는 악순환.
- **flap(플랩)** — 상태가 UP↔DOWN을 빠르게 깜빡이는 것. 그래서 '연속 3회 실패'처럼 여러 번 확인 후에만 차단한다.
- **shard red(샤드 레드)** — ES 데이터를 쪼갠 조각(shard) 중 일부가 망가진 상태. 전체는 살아 있어 ping은 통과하지만 일부 검색이 빠진다.

## 다시 검토할 시점
- ES multi-cluster (read replica) 도입 시 readiness 판정을 "primary down + replica also
  down" 으로 확장.
- liveness 가 너무 둔감해 hang 상태에서 자동 재시작이 안 일어나는 사례가 보이면 별도
  liveness indicator 추가 (예: 마지막 successful search 가 N분 이상 없을 때 down).
