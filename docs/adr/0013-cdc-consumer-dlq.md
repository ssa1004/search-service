# ADR-0013: CDC consumer DLQ (DefaultErrorHandler + DLT) + 운영자 manual replay

## 상태
적용

## 배경
기존 `CdcConsumer` 는 처리 실패 시 ack 안 하고 예외를 던져 *무한 재시도* 가 default. 두 문제:
- payload schema 오류 / corrupted JSON 처럼 **영구 실패** 인 메시지가 같은 partition 의 처리를
  영구히 막는다 (poison pill).
- consumer lag 메트릭이 끝없이 증가 — alert 만 시끄럽고 root cause 는 한 메시지.

해결 방향:
- 즉시 retry 수회 후 DLT 로 격리.
- DLT 는 별도 컨슈머가 보존 + 운영자 manual replay endpoint 로 재처리.

## 결정
**Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` + `FixedBackOff(0, 2)`.**

### 흐름
1. CdcConsumer.onMessage 가 예외 → ListenerContainer 가 DefaultErrorHandler 호출.
2. DefaultErrorHandler 가 FixedBackOff(0ms, 2회 retry) 로 같은 메시지 즉시 재시도. 총 3회 (첫 호출 + 2 retry).
3. 3회 모두 실패 시 DeadLetterPublishingRecoverer 가 `<원본>.DLT` 토픽 + 같은 partition 으로 publish.
4. CdcDltConsumer 가 DLT 를 ack 만 하고 in-memory 카운터 업데이트.
5. 운영자가 root cause 수정 후 `POST /api/v1/admin/cdc/dlt/replay?maxRecords=N` 호출 →
   DLT earliest 부터 batch 만큼 원본 토픽 재발행.

### Backoff 선택 근거
- `interval=0` — CDC 의 실패는 통상 transient (idempotent retry 가능) 이거나 영구 (schema
  오류). 전자는 0ms 로도 회복, 후자는 wait 시간이 의미 없음. 짧게 끝낸다.
- `maxAttempts=2` (총 3회) — 통상 운영 권고. 필요시 yaml 외부화.

### 메트릭
| metric | tags | 의미 |
| --- | --- | --- |
| `cdc.consume` | topic, outcome=success | 정상 처리 |
| `cdc.consume` | topic, outcome=retry | DefaultErrorHandler 가 retry 호출 |
| `cdc.consume` | topic, outcome=dlt | DLT 로 격리됨 |
| `cdc.dlt.observed` | topic | DLT 컨슈머가 본 메시지 수 |

`outcome=dlt` rate 가 spike 하면 즉시 alert.

## 장단점
- 장점: poison pill 이 partition 처리를 막지 않음 — lag 이 한 메시지 때문에 폭주 안 함.
- 장점: 메트릭으로 retry / dlt 분리 카운트 — root cause 진단 빠름.
- 장점: replay endpoint 로 운영자가 fix 후 즉시 재처리 가능 (storage 외부화 없이 in-memory 보존만으로
  검색 service 의 사용 케이스에 충분 — DLT 보존은 Kafka 의 retention 에 위임).
- 단점: DLT 보존이 Kafka topic retention 정책에 종속 — 보존 기간 짧으면 운영자가 손쓰기 전에
  손실 가능. retention 을 7일+ 로 명시.
- 단점: replay 가 같은 메시지를 여러 번 publish 가능 — consumer 의 idempotency 가 전제 (현재
  ES external version 비교로 충족).

## 다시 검토할 시점
- DLT 메시지가 일시적이지 않게 누적되면 별도 storage (DB/S3) 로 archive + UI 검토 도구 추가.
- 영구 schema 오류 비율이 높아지면 producer 측 schema 검증 (Schema Registry / Avro) 도입 검토.
- replay endpoint 의 보안 (현재는 보안 게이트 외부 의존) — service-account / OIDC 로 강화.
