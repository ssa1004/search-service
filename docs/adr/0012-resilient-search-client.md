# ADR-0012: ES 호출 Resilience4j Retry + CircuitBreaker decorator

## 상태
적용

## 배경
ES 가 일시 unavailable / overload 상태일 때 검색 endpoint 가 그대로 5xx 를 caller 로 전파하면
- 검색은 latency 민감한 hot path — 타임아웃 누적이 그대로 사용자 응답 지연.
- caller (web 프런트 / 모바일) 가 retry 하면 cluster 가 더 죽는 retry storm.
- 직전 ADR-0010 의 readiness 만으로는 부족 — readiness 가 DOWN 으로 전환되기 전 수 초 동안
  cascade fail 발생.

기존 구현은 `@CircuitBreaker` + `@Retry` 어노테이션만 부여. 두 어노테이션이 같은 메서드에 있을
때 **순서가 명시적이지 않다** (Spring AOP order property 로만 제어 — 잘못 잡히면 CB 가 바깥에
앉아 Retry 의 모든 시도를 한 호출로 카운트 → 실패율 산정 왜곡).

## 결정
**프로그래매틱 데코레이터 `ResilientSearchClient`** 로 명시 wrap.

- 어노테이션 제거 — `ElasticsearchSearchEngineAdapter` 는 raw ES 호출만 책임.
- bootstrap 의 `ElasticsearchConfig` 가 `Retry` + `CircuitBreaker` 를 주입해 `ResilientSearchClient`
  로 wrap → `SearchEnginePort` 빈으로 등록.
- chain 순서: **Retry → CircuitBreaker** (Retry 가 바깥). CB 가 inner 여야 각 시도가 CB 의 한
  call 로 카운트되어 실패율 산정 정확.

### 튜닝 (검색 latency 민감성 반영)

| 항목 | 값 | 근거 |
| --- | --- | --- |
| Retry max-attempts | 3 | 일시 GC pause / network blip 흡수 (초과는 latency 만 누적) |
| Retry wait | 100ms | 검색은 latency-sensitive — 200ms 이상 wait 은 사용자 체감 |
| Retry exp | 2.0 | 100ms → 200ms → 400ms — 두 번째 시도까지 600ms 안에 |
| Retry jitter | 0.5 | 동시 요청들의 retry 시점 분산 (thundering herd 방지) |
| CB sliding window | 50, count-based | 50 호출 단위로 평가. 작은 트래픽에서도 의미 있음 |
| CB minimumNumberOfCalls | 20 | window 채우기 전엔 평가 안 함 (false positive 방지) |
| CB failure rate | 50% | 운영 노이즈 흡수 후의 보수적 임계 |
| CB wait OPEN | 30s | 짧으면 cluster 회복 전 재시도, 길면 사용자 영향 길어짐 |
| CB half-open calls | 5 | 회복 판정에 충분, 여전히 OPEN 이면 적은 trial 로 빠르게 재차단 |

### 영향 범위
search / autocomplete / findRelatedKeywords 모두 같은 chain 적용. memory 모드는 wrap 안 함.

## 장단점
- 장점: chain 순서가 코드에 명시 — 유지보수자가 어노테이션 AOP 의 미묘한 order 를 추적 안 해도 됨.
- 장점: 단위 테스트 (`ResilientSearchClientTest`) 가 Spring 무관 — Retry / CB 동작 검증 빠름.
- 장점: Retry → CB 순서가 보장되어 실패율 메트릭 신뢰 가능.
- 단점: 검색 인덱싱 (write) 는 본 chain 적용 안 됨 — write 는 별도 idempotency / 멱등성 정책
  (외부 versioning) 으로 처리.
- 단점: 어노테이션 제거로 다른 `SearchEnginePort` 구현체 (e.g. test stub) 는 자동 보호 안 됨 —
  의도된 설계 (port 의 의미는 "검색 엔진" 이지 "보호된 검색 엔진" 이 아님).

## 다시 검토할 시점
- 운영 메트릭에서 ES p99 가 100ms × 3 = 300ms 보다 훨씬 느린 경우 — wait 시간이 사용자 응답에
  병목이 되므로 max-attempts 줄이거나 timeout 추가 검토.
- IndexWriter 호출에도 비슷한 cascade 문제가 보이면 별도 ResilientIndexWriter 로 분리 (write 는
  retry 정책이 다름 — idempotency 보장 후에만 retry).
- bulkhead (동시 호출 수 제한) 추가 검토 — slow ES 응답이 thread 를 점유해 다른 endpoint 까지
  영향 줄 때.
