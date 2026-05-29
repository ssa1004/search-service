# 백엔드 스킬 인덱스 — 이 레포에서 무엇을 배우나

> 이 레포가 시연하는 검색 백엔드 / 운영 패턴을 **"무엇 → 이 레포 어디서 → 왜(ADR) → 더 깊은 이론"** 으로 잇는 학습용 인덱스.
> "이 패턴 공부하려면 어디부터 보나"의 진입점. 설명을 다시 쓰지 않고 코드·결정·이론으로 연결만 한다.

## 검색 엔진 (Elasticsearch)

| 패턴 | 이 레포 어디서 | 왜 (ADR) | 한 줄 |
|------|---------------|---------|-------|
| **multi-field mapping (text + keyword + autocomplete)** | `search-bootstrap` ES mapping JSON (`name` / `name.keyword` / `name.autocomplete`) | [ADR-0003](adr/0003-mapping-multi-field.md) | 한 필드를 매칭·정렬/집계·자동완성 세 형태로 동시 색인 |
| **nori 한국어 형태소 + user_dictionary** | `docker/elasticsearch` Dockerfile (nori 플러그인) + ES analyzer 설정 | [ADR-0015](adr/0015-nori-korean-analyzer.md) | "조던1" 을 단일 토큰 보존 + 조사/어미 제거 — 한국어 recall |
| **자동완성 — edge_ngram vs completion suggester** | `name.autocomplete` (edge_ngram 1-10) | [ADR-0007](adr/0007-autocomplete-edge-ngram-vs-completion.md) | 검색과 같은 query DSL 재사용 — 운영 단순화 |
| **function_score boost (인기도 + 신상품)** | `search-domain` `BoostRule` + `search-adapter-out` ES query 빌드 | [ADR-0006](adr/0006-boost-rule-relevance.md) | `log1p(clickCount)` × popularity + 출시일 `gauss decay` 를 BM25 에 곱함 |
| **facet aggregation cardinality 상한** | `search-domain` `FacetSpec` (terms size ≤ 100 강제) | [ADR-0008](adr/0008-facet-cardinality-memory-protection.md) | `size:1M` 같은 위험 집계를 도메인 단계에서 차단 — OOM 방지 |
| **운영자 동의어 사전 + 런타임 reload** | `search-adapter-out` 동의어 sync (close→putSettings→open) | [ADR-0017](adr/0017-synonym-graph-and-runtime-reload.md) | `synonym_graph` filter, RDB 가 진실값, 재시작 없이 반영 |

→ 이론: `dev-lab/opensearch` (역색인 / analyzer / text vs keyword / query DSL — ES 와 같은 Lucene 엔진)

## 무중단 인덱스 운영

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **alias 기반 zero-downtime reindex** | `POST /api/v1/admin/index/reindex` (alias `products` → 물리 인덱스 atomic swap) | [ADR-0005](adr/0005-alias-zero-downtime-reindex.md) | mapping/analyzer 변경 시 새 인덱스 bulk → doc count 일치 시에만 swap |
| **ES external version 멱등 색인** | `search-adapter-out` ES index 호출 | [ADR-0004](adr/0004-cdc-outbox-pipeline.md) | 같은 메시지 2회 처리돼도 더 낮은 version 은 무시 — 재발행 안전 |

→ 이론: `dev-lab/opensearch` (alias / reindex / mapping 불변성)

## CDC 색인 파이프라인 (Outbox + Kafka)

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **Outbox 패턴 (Debezium 스타일 시뮬)** | `search-adapter-out` outbox 테이블 + `CdcOutboxRelay` (5초 polling) | [ADR-0004](adr/0004-cdc-outbox-pipeline.md) | source DB 변경과 같은 트랜잭션으로 outbox INSERT — dual-write 해소 |
| **at-least-once consumer + 멱등** | `search-adapter-in` `CdcConsumer` → `product.changes` | ADR-0004 | ES 장애가 source 도메인을 멈추지 않음 + 멱등 use case |
| **CDC consumer DLQ + manual replay** | `DefaultErrorHandler` + DLT (`product.changes.DLT`) + `POST /api/v1/admin/cdc/dlt/replay` | [ADR-0013](adr/0013-cdc-consumer-dlq.md) | 실패 메시지 격리 → 원인 해결 후 운영자 재처리 |
| **Outbox retention + ShedLock** | `search-adapter-out` `OutboxRetentionJob` (`@Scheduled` + `@SchedulerLock`) | [ADR-0014](adr/0014-outbox-retention.md) | published 행 정리 + 멀티 인스턴스 중 한 대만 실행 |

→ 이론: `dev-lab/cdc` (Outbox vs WAL/Debezium), `dev-lab/kafka` (at-least-once / DLQ / consumer)

## 저장 검색 + 알림 (cursor 기반)

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **cursor pagination (신규 매치만 평가)** | `search-domain` `SavedSearch.evaluationCursor` + `findActiveBatchAfter` batch 로딩 | [ADR-0016](adr/0016-saved-search-and-alerts.md) | 매번 전체 결과 대신 cursor 이후 신규만 — offset 폭주/중복 회피 |
| **pull 기반 평가 스케줄러 + ShedLock** | `SavedSearchEvaluatorJob` (5분 주기, `@SchedulerLock`) | ADR-0016 | 신규 product 폭주에도 batch 처리 — push 대신 pull 로 부하 분산 |
| **알림 채널 분리 (Outbox)** | `alertPublisher` → Kafka `search.alert.fired` | ADR-0016 | 매치 감지(본 service) ↔ 채널 발송(notification-hub) 책임 분리 |

→ 이론: `dev-lab/api-design` (cursor vs offset pagination), `dev-lab/kafka` (이벤트 발행 + 소비 분리)

## 회복탄력성 (Resilience)

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **ES 호출 Circuit Breaker + Retry** | `search-adapter-out` `ResilientSearchClient` (Resilience4j decorator) | [ADR-0012](adr/0012-resilient-search-client.md) | ES 실패율 임계 초과 시 즉시 회로 차단 — 긴 응답 지연 격리 |
| **HikariCP 명시 튜닝 + leak detection** | `search-bootstrap` DataSource config | [ADR-0009](adr/0009-hikari-pool-tuning.md) | 커넥션 풀 포화 / 누수 조기 검출 |
| **K8s 3종 probe + ES 종속 readiness** | `search-bootstrap` `ApplicationReadinessCoordinator` | [ADR-0010](adr/0010-k8s-probes.md) | ES 미준비 시 readiness 실패 — 트래픽 유입 전 의존성 확인 |
| **graceful shutdown + preStop** | `search-bootstrap` + helm deployment preStop | [ADR-0011](adr/0011-graceful-shutdown.md) | 종료 시 in-flight 요청 drain — 무중단 롤링 배포 |

→ 이론: `dev-lab/resilience` (circuit breaker / retry / bulkhead), `dev-lab/observability` (probe / SLI)

## 검색 query analytics

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **Top / Zero-result / Latency / CTR 분석** | `search-events` 기록 + `GET /api/v1/admin/analytics/*` | [ADR-0018](adr/0018-query-analytics.md) | 검색 호출마다 기록 → 0건 검색어가 동의어/boost 운영의 입력 |

→ 이론: `dev-lab/observability` (RED — request/error/duration, 운영 지표화)

## 아키텍처

| 패턴 | 이 레포 어디서 | 왜 | 한 줄 |
|------|---------------|-----|-------|
| **헥사고날 (ports & adapters) 6모듈** | `search-domain` / `-application` / `-adapter-in` / `-adapter-out` / `-bootstrap` / `e2e-tests` | [ADR-0001](adr/0001-hexagonal-architecture.md) | 도메인이 ES/Kafka SDK 를 모름 → `InMemorySearchEngineAdapter` 로 ES 없이 동일 흐름 |
| **검색 엔진 추상화 (port 교체)** | `search-application` port + `InMemory` / ES adapter | [ADR-0001](adr/0001-hexagonal-architecture.md), [ADR-0002](adr/0002-elasticsearch-vs-opensearch.md) | ES ↔ OpenSearch 교체가 adapter 교체로 국한 |

→ 이론: `dev-lab/system-design` (헥사고날 / 포트&어댑터 / 모듈 경계)

## 학습 순서 제안 (이 레포 기준)

1. **[README](../README.md) 상단 + 시스템 흐름 다이어그램** → 검색 / CDC / 클릭 학습 전체 그림
2. **[ADR-0001](adr/0001-hexagonal-architecture.md)** (헥사고날) → 모듈 경계부터 — 코드 읽기 전 지도
3. **검색 엔진 표** (mapping / nori / boost / facet / 동의어) → 코드 + 해당 ADR + `dev-lab/opensearch`
4. **CDC 파이프라인 표** (Outbox → Kafka → ES) → ADR-0004/0013/0014 + `dev-lab/cdc`·`dev-lab/kafka`
5. **저장 검색 + cursor 표** → ADR-0016 + `dev-lab/api-design` (cursor pagination)
6. **회복탄력성 / analytics 표** → 운영자 관점 (ES 격리 · 0건 검색 → 동의어 루프)

> 짝 학습 레포: [dev-lab](https://github.com/ssa1004/dev-lab) (이론) ↔ 이 레포 (구현). 이론에서 "왜"를, 여기서 "실제로 어떻게"를 본다. 특히 `dev-lab/opensearch` 의 역색인 / analyzer / query DSL 을 먼저 보면 이 레포의 mapping·nori·function_score 가 빠르게 잡힌다.
