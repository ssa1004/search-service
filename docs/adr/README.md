# Architecture Decision Records (ADR)

본 디렉토리는 search-service 의 핵심 설계 결정과 그 근거를 담는다. 각 ADR 은 짧게 읽고도
결정과 장단점을 파악할 수 있도록 작성한다.

| 번호 | 제목 |
|------|------|
| [ADR-0001](0001-hexagonal-architecture.md) | 헥사고날 아키텍처 + 모듈 분리 |
| [ADR-0002](0002-elasticsearch-vs-opensearch.md) | Elasticsearch vs OpenSearch — 라이선스 + 운영 표준 |
| [ADR-0003](0003-mapping-multi-field.md) | ES Mapping 설계 — multi-field (text + keyword + autocomplete) |
| [ADR-0004](0004-cdc-outbox-pipeline.md) | CDC 기반 indexing pipeline — Debezium 스타일 outbox 시뮬 |
| [ADR-0005](0005-alias-zero-downtime-reindex.md) | alias-based zero-downtime reindex |
| [ADR-0006](0006-boost-rule-relevance.md) | boost rule + relevance tuning — function_score, click-through rate |
| [ADR-0007](0007-autocomplete-edge-ngram-vs-completion.md) | 자동완성 — edge_ngram vs completion suggester |
| [ADR-0008](0008-facet-cardinality-memory-protection.md) | facet aggregation 의 cardinality 제한 + 메모리 보호 |
| [ADR-0009](0009-hikari-pool-tuning.md) | HikariCP 명시 튜닝 + connection leak detection |
| [ADR-0010](0010-k8s-probes.md) | K8s 3종 probe + ES 종속 readiness coordinator |
| [ADR-0011](0011-graceful-shutdown.md) | Spring Boot graceful shutdown + K8s preStop |
| [ADR-0012](0012-resilient-search-client.md) | ES 호출 Resilience4j Retry + CircuitBreaker decorator |
| [ADR-0013](0013-cdc-consumer-dlq.md) | CDC consumer DLQ (DefaultErrorHandler + DLT) + manual replay |
| [ADR-0014](0014-outbox-retention.md) | Outbox retention 정리 스케줄 + ShedLock 멀티 인스턴스 보호 |
| [ADR-0015](0015-nori-korean-analyzer.md) | nori 한국어 형태소 analyzer + user_dictionary |
| [ADR-0016](0016-saved-search-and-alerts.md) | 저장 검색 (Saved Search) + 신규 매치 알림 |
| [ADR-0017](0017-synonym-graph-and-runtime-reload.md) | 운영자 동의어 사전 + ES synonym graph 런타임 reload |
| [ADR-0018](0018-query-analytics.md) | 검색 query analytics — Top / Zero-result / Latency / CTR |
