# ADR-0004: CDC 기반 indexing pipeline — Debezium 스타일 outbox 시뮬

## 상태
적용

## 배경
검색 인덱스의 일관성은 source DB (Postgres) 의 INSERT/UPDATE/DELETE 가 ES 에 빠짐 없이
반영되어야 한다. naive 한 방식 (도메인 service 가 DB save 후 ES index 를 직접 호출) 은
다음 이유로 운영에서 깨진다.
- DB commit 후 ES 호출 직전에 프로세스 죽으면 ES 가 영원히 stale.
- ES 호출 실패를 트랜잭션 롤백으로 묶으면 ES 장애가 결제 / 상품 등록을 막는다.
- ES 가 수십 ms 추가 latency 를 줄 수 있어 호출 흐름이 길어진다.

운영의 답은 **Change Data Capture (CDC)** — DB 변경을 외부에서 관찰하고 별도 워커가 ES 에
반영. Debezium 이 표준이지만 본 프로젝트 규모에선 도입 / 운영 부담이 큰 편.

## 결정
**Outbox 패턴 + Kafka 컨슈머로 Debezium 동작을 단순화 시뮬레이션.**

```
도메인 트랜잭션
  ├── INSERT INTO products (...)
  └── INSERT INTO product_change_outbox (...)        ← 같은 트랜잭션
         ↓ (commit)
       CdcOutboxRelay (5초 polling)
         ↓
       Kafka topic "product.changes"
         ↓
       CdcConsumer
         ↓
       HandleProductChangeUseCase
         ↓
       ES index / delete
```

핵심 보장:
- DB commit + Kafka publish 의 atomicity — outbox 에 같은 트랜잭션으로 INSERT 후 별도
  relay 가 published_at 을 채움. publish 성공 + DB update 실패 시 다음 polling 에 재발행
  (멱등 — ES external version 이 같은 또는 더 큰 version 만 받음).
- ES 장애가 source 도메인 흐름에 영향 없음.
- Kafka 컨슈머는 at-least-once + 멱등 처리 (use case 가 idempotent).

## 장단점
- 코드는 단순 — Postgres 표준 SQL + Spring scheduling 만으로 동작.
- 운영 부담 작음 — Debezium connector 클러스터 / Schema Registry 운영이 없음.
- 처리 latency 가 polling interval (5초) 에 묶임. 실시간성이 더 필요하면 interval 줄이거나
  Debezium 도입.
- outbox 테이블이 무한 누적되면 비대화 — 별도 retention 스케줄로 정리 (ADR-0014).

## 다시 검토할 시점
- 검색 결과 stale 의 SLA 가 1초 이내로 강해질 때 — Debezium 도입.
- 멀티 source (products + reviews + ...) 로 늘어나 스키마 관리가 복잡해질 때 — Debezium +
  Schema Registry.
