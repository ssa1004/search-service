# ADR-0014: Outbox retention 정리 스케줄 + ShedLock 멀티 인스턴스 보호

## 상태
적용

## 배경
`product_change_outbox` 는 CDC pipeline 의 임시 보관소 — relay 가 publish 후 `published_at` 만
채우고 row 는 그대로 둔다. 운영에서:

- 일 평균 N row 의 INSERT/UPDATE/DELETE 가 outbox 에 쌓이고 retention 정리 없으면 무한 증가.
- 무한 증가 → relay 의 `findUnpublished` 가 `published_at IS NULL` index 로 빠르더라도 vacuum
  비용 / 백업 부피가 누적적으로 부담.
- Debezium 운영체제도 비슷한 문제 — 소스 DB 의 WAL 보관 정책이 동일 패턴.

## 결정
**`OutboxRetentionJob` (@Scheduled cron 매일 03:00 KST) + ShedLock 멀티 인스턴스 보호.**

### 정책
- retention = 7일 (가시적 디버깅 / 메시지 손실 복구 가능 기간).
- batch 1000 row 단위 DELETE — 한 transaction 너무 커지지 않게 (long-running → leak detection
  경고, WAL 부담).
- BATCH_SIZE 가득 차면 다시 호출하여 drain.

### ShedLock
- 멀티 인스턴스 환경에서 같은 cron 에 여러 pod 이 동시 실행되면 같은 row 를 읽고 같은 row 를
  지우려는 경합 발생 — 한 인스턴스만 실행 보장이 필요.
- `JdbcTemplateLockProvider` + `shedlock` 테이블 (Flyway V2). `usingDbTime()` 로 노드 간 clock
  skew 무관.
- `lockAtMostFor=10m` — pod crash 후에도 10분 후 다른 pod 이 재실행. `lockAtLeastFor=1m` —
  job 이 빨리 끝나도 1분간 다른 pod 막아 race 회피.

### 메트릭
- `outbox.retention.deleted` — counter. 매일 spike 가 있으면 정상, 0 이 지속되면 retention 미동작.

## 장단점
- 장점: outbox 무한 증가 방지 — vacuum 비용 / 백업 부피 안정.
- 장점: ShedLock 으로 멀티 인스턴스 안전 — 같은 패턴이 billing / GPU 도메인과 동일 (운영자 학습
  비용 0).
- 장점: 메트릭으로 동작 가시화 — 매일 03:00 직후 spike 가 안 보이면 즉시 알림.
- 단점: 7일 retention 은 임의값 — DR / 감사 정책에 따라 외부화 필요할 수 있음. 현재는 코드 상수.
- 단점: cron 이 운영 시간이 아닌 03:00 KST — 운영 timezone 전제 (Asia/Seoul). 글로벌 배포 시
  재고.

## 다시 검토할 시점
- outbox 증가 속도가 일 1M row 를 넘으면 batch_size 키우거나 cron 주기 줄임.
- cluster 가 multi-region 으로 가면 ShedLock 의 DB 의존이 cross-region latency 로 느려질 수
  있음 — region 별 cron + region 별 lock 으로 분리.
- DR / 감사 요구가 들어오면 retention 을 7d → 30d 로 확장하고 별도 archive (S3) 로 cold storage.
