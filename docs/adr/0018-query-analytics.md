# ADR-0018: 검색 분석 (Query Analytics)

## 상태
적용

## 배경

검색은 *돌리는 것* 자체가 아니라 *품질이 운영 KPI* 다. 운영팀은 다음을 보고 싶다.

- 가장 많이 검색된 keyword — 신상품 / 카테고리 노출 결정.
- 결과 0건이 자주 났던 keyword — 동의어 / 사전 보강 후보 (ADR-0017 과 직접 연결).
- 응답 latency p50/p95/p99 — 사용자 체감 느려진 시점 알람.
- 클릭 전환율 (CTR) — 결과 품질이 실제 사용자 행동에 도움이 되는지.

이 데이터 없이는 "검색이 좋아졌는가" 가 *주관* 의 영역에 머문다 — 모든 검색 운영 팀의 표준
대시보드.

## 결정

### SearchEvent 도메인 — 한 검색의 관측 결과

```
SearchEvent
  searchId      ← 클릭 이벤트와 join 키
  keyword       ← 정규화 (trim + lowercase)
  userId        ← null 가능 (익명 검색)
  resultCount   ← 0 이면 zero-result 후보
  latencyMs     ← ES took
  occurredAt
```

`searchId` 는 SearchProductCommand 가 받아 SearchEvent 에 그대로 옮긴다. 같은 searchId 로
저장된 클릭 이벤트가 join 가능 — CTR 계산은 (검색 결과 1건 이상이었던 검색 수) ÷ (해당 구간
클릭 수).

### 비동기 INSERT — 검색 latency 보호

`SearchEventRecorder` 를 outbound port 로 두고 `@Async` 어댑터가 별도 thread pool 에서 INSERT.
검색 응답 thread 가 분석 저장을 기다리지 않는다.

```kotlin
@Async("searchAnalyticsExecutor")
@Transactional(propagation = Propagation.REQUIRES_NEW)
override fun record(event: SearchEvent) { repository.save(...) }
```

전용 pool — core 4 / max 8 / queue 1000 + `DiscardOldestPolicy`. 분석은 표본 기반이라 일부 손실
허용. caller 차단 / 검색 응답 latency 흔들림 회피가 우선.

`SearchProductService` 는 `ObjectProvider<SearchEventRecorder>` 로 lazy 의존 — recorder 빈이
없는 환경 (단위 테스트 / 분석 비활성) 에서 자동 no-op.

### 4가지 분석 use case 통합 인터페이스

`QueryAnalyticsUseCase` 한 인터페이스에 4 메서드:
- `topQueries(from, to, limit)`
- `zeroResultQueries(from, to, limit)`
- `queryLatencyPercentiles(from, to)`
- `clickThroughRate(from, to)`

운영 화면 한 곳이 4개를 같은 구간 입력으로 호출하므로 분리하면 컨트롤러 보일러플레이트만
늘어남. limit 은 service 단계에서 100 으로 cap — 응답 크기 / 화면 표시 보호.

### Latency percentile — 메모리 계산

H2 / Postgres 양쪽 호환을 위해 SQL `percentile_cont` 대신 정렬된 `latency_ms` 리스트를 받아
nearest-rank 방식으로 계산:

```kotlin
val idx = ceil(p * n).toInt() - 1
return sortedAsc[idx]
```

표본 ~수만 까지는 메모리 정렬이 안전. 초당 수천 건 이상 트래픽이면 ClickHouse / BigQuery 등
전용 OLAP 으로 옮기는 게 자연스럽다.

### CTR 분모는 결과 1건 이상 검색만

```
rate = clicks / searches_with_results
```

zero-result 검색을 분모에 포함하면 zero-result 가 늘 때마다 CTR 이 *자연 하락* 한다 — 결과
품질과 무관한 변동. 사용자가 클릭할 게 아예 없었던 검색은 CTR 계산에서 제외하는 게 시그널
정확.

### 인덱스

```sql
CREATE INDEX ix_search_events_occurred         ON search_events (occurred_at);
CREATE INDEX ix_search_events_occurred_keyword ON search_events (occurred_at, keyword);
```

모든 분석 query 가 시간 구간 필터로 시작하므로 `occurred_at` 첫 컬럼. group-by 까지 cover 하는
복합 인덱스 추가.

## 대안

### 모든 검색을 Kafka topic 으로 발행 후 별도 consumer 가 OLAP 적재
검토 → 후속. 트래픽이 일정 규모를 넘으면 운영 RDB 가 분석 + OLTP 동시 부담을 못 견딘다. 그
시점에 outbox + Kafka + ClickHouse 로 옮기는 게 자연스럽다. 본 ADR 의 인터페이스
(`SearchEventRecorder` / `SearchEventAnalyticsRepository`) 가 그대로 새 구현체로 교체 가능.

### Elasticsearch 자체 query log
검토 → 보완. ES slow log 는 latency 만 잡고 keyword 정규화 / userId / 비-slow query 까지의 분포는
못 본다. 본 ADR 의 SearchEvent 가 더 풍부한 차원을 잡지만 ES slow log 와 cross check 는 가능.

### Async 없이 동기 INSERT
탈락 — 운영 환경에서 search_events INSERT 가 검색 응답 thread 의 critical path 에 들어가면
DB outage 가 곧 검색 outage. `@Async + DiscardOldestPolicy` 로 검색을 분석 실패에서 격리.

### Redis HyperLogLog / Sketch
검토 → top queries / zero-result 의 distinct 카운팅에는 sketch 가 메모리 절약. 하지만 본 ADR 의
규모에서는 RDB GROUP BY 가 충분하고, sketch 는 % 오차가 있어 운영 화면의 "정확한 횟수" 와 안
맞는다.

## 결과

- 운영팀이 검색 품질을 *데이터 기반* 으로 본다 — 신조어 발견, latency regression, CTR 추적.
- 비동기 INSERT 로 검색 응답 latency 영향 0 (`DiscardOldestPolicy` 로 burst 도 흡수).
- 분석 use case 인터페이스 4개 — 운영 화면 한 곳에서 모두 호출.
- (단점) RDB 가 분석 + OLTP 동시 — 장기엔 OLAP 분리가 자연스럽다.
- (단점) percentile 메모리 계산 — 표본 수십만 이상이면 OLAP 으로 옮길 시점.
- (단점) `DiscardOldestPolicy` 로 일부 이벤트 손실 — 운영 화면이 표본 통계라는 점이 명시되어야.

## 용어 풀이 (쉽게)

- **CTR (Click-Through Rate, 클릭 전환율)** — 검색 결과를 보여줬을 때 실제로 클릭된 비율. 결과가 사용자에게 쓸모 있었는지 보는 지표.
- **latency percentile (p50/p95/p99)** — 응답 시간을 줄 세웠을 때 50%·95%·99% 지점의 값. p99는 '가장 느린 축 1%'의 체감으로, 평균만 보면 놓치는 느린 사용자를 잡는다.
- **zero-result(0건 검색)** — 결과가 하나도 안 나온 검색. 동의어·사전 보강이 필요한 후보를 찾는 단서.
- **비동기(@Async) INSERT** — 분석 기록 저장을 검색 응답과 떼어 내, 검색이 그 저장을 기다리지 않고 바로 응답하게 하는 것. 주문은 바로 받고 영수증 정리는 뒤에서 따로.
- **DiscardOldestPolicy(오래된 것 버리기)** — 처리 대기열이 꽉 차면 가장 오래된 기록 하나를 버리고 계속 받는 정책. 분석은 표본이라 일부 손실을 허용한다.
- **OLAP / ClickHouse·BigQuery** — 대량 데이터를 빠르게 통계·분석하는 데 특화된 전용 저장소. 트래픽이 커지면 일반 DB 대신 이쪽으로 옮긴다.
- **sketch / HyperLogLog(스케치)** — '대략 몇 종류더라'를 적은 메모리로 어림셈하는 기법. 빠르지만 약간의 오차가 있어 '정확한 횟수'가 필요한 화면엔 안 맞는다.

## 후속

- ADR (예정): zero-result 분석 결과 → 동의어 자동 추천 (ADR-0017 과 cross)
- ADR (예정): SearchEvent → Kafka outbox + ClickHouse 적재
- ADR (예정): 운영 RDB 의 search_events 월별 partition + 90일 retention
