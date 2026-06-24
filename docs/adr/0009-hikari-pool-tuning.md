# ADR-0009: HikariCP 명시 튜닝 + connection leak detection

## 상태
적용

## 배경
Spring Boot 가 default 로 두는 HikariCP 설정은 부트스트랩 편의성에 맞춰져 있다 — 운영
프로파일에는 부족하다.

문제 상황 세 가지:
- `maximum-pool-size=10` (default) — virtual thread + JPA 동시 transaction 이 10 을 넘으면
  caller 가 풀 대기로 직렬화. 검색은 read 위주여도 click 기록 / outbox INSERT 는 write
  transaction.
- leak detection 없음 — `EntityManager` 가 close 안 되거나 long-running transaction 이
  생겨도 풀이 천천히 마르다 OOM/timeout 으로 드러남. 이미 사고 난 뒤에야 추적 시작.
- `max-lifetime=30분` (default) — Postgres / pgbouncer / cloud-NAT idle timeout (보통 30분
  내외) 와 정확히 겹치면 서버측 close 와 race — `Connection is closed` 가 간헐적으로 발생.

## 결정
`spring.datasource.hikari.*` 를 application.yml 에 **모든 핵심 값을 명시**. test 프로필은
별도 override.

운영 값:

| 항목 | 값 | 산정 근거 |
| --- | --- | --- |
| `maximum-pool-size` | 16 | virtual thread + JPA 환경에서 pod 1대당 동시 transaction 16 수준이면 충분. Postgres `max_connections=100` 가정 시 6 pod * 16 = 96 — 헤드룸 확보. |
| `minimum-idle` | 4 | trough 시간에도 4개는 idle 로 유지 — burst 시 cold connection 오버헤드 회피. |
| `connection-timeout` | 3s | 풀 고갈 시 caller 가 빠르게 fail — 검색 latency 보호 (요청이 줄 서지 않게). |
| `max-lifetime` | 29분 | 서버측 idle timeout (보통 30분) 보다 1분 짧게 — race 회피. |
| `keepalive-time` | 10분 | NAT / firewall 의 idle drop 방지. max-lifetime 의 절반 미만. |
| `idle-timeout` | 10분 | minimum-idle 위에서만 trim. |
| `leak-detection-threshold` | 30s | 30s 이상 미반환 connection 의 stack trace 남김 — close() 누락 / long transaction 즉시 식별. |

테스트 프로필 (`application-test.yml`) 은 leak detection 비활성, pool=4 — Testcontainers
shutdown 직전 trim 이나 SQL script 가 30s 넘기는 경우의 false positive 회피.

## 장단점
- 장점: 운영 default 회귀로 인한 부하 사고 방지. leak 은 첫 발생 시 stack trace 와 함께
  잡혀 디버깅 시간 단축.
- 장점: 회귀 테스트 (`HikariPoolConfigTest`) 로 starter 교체 / yaml 오타 회귀 보호.
- 단점: pool=16 은 boilerplate 한 추정값 — 실 운영 부하 측정 후 조정 필요.
- 단점: leak-detection-threshold 가 30s 면 정상 long-running batch (예: reindex) 가 발생시킬
  수 있음 — batch 작업은 별도 datasource 분리 검토 가치.

## 용어 풀이 (쉽게)

- **HikariCP / 커넥션 풀** — DB 연결을 미리 여러 개 만들어 두고 빌려주는 대여소. 매번 새로 연결하면 느리니 미리 뚫어 둔 연결을 돌려 쓴다.
- **풀 포화 / connection leak** — 포화는 빌려줄 연결이 동나 새 요청이 줄 서다 멈추는 것. leak은 빌린 연결을 안 돌려줘 조용히 사라지는 것(공유 우산을 안 반납해 다 떨어진 셈).
- **leak detection** — 30초 넘게 안 돌려준 연결의 흔적(어디서 빌려갔는지)을 로그로 찍어 범인을 잡는 기능.
- **가상 스레드(Virtual Thread)** — OS가 직접 관리하는 무거운 스레드 대신 JVM이 관리하는 아주 가벼운 스레드. 적은 자원으로 동시에 많은 일을 받는다.
- **max-lifetime / idle timeout** — 연결을 너무 오래·놀린 채 두지 않게 일정 시간 지나면 끊는 설정. 서버가 몰래 끊은 연결을 모르고 쓰다 나는 오류를 피한다.
- **pgbouncer** — DB 앞에 두는 연결 중개소. 수많은 앱 연결을 적은 실제 DB 연결로 묶어 DB 부담을 줄인다.

## 다시 검토할 시점
- 실 운영에서 hikari `pending` 메트릭 (대기 thread 수) 이 0 보다 자주 튀면 pool 키운다.
- Postgres `max_connections` 가 줄거나 pgbouncer transaction pooling 으로 이행 시 pool 재산정.
- reindex / outbox retention 같은 batch 가 30s 이상 connection 보유로 leak 경고 자주 띄우면
  separate hikari pool 또는 `@Transactional` 분리 검토.
