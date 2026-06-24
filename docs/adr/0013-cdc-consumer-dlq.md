# ADR-0013: CDC consumer DLQ (DefaultErrorHandler + DLT) + 운영자 manual replay

## 상태
적용

## 배경
기존 `CdcConsumer` 는 처리 실패 시 ack 안 하고 예외를 던져 무한 재시도가 default. 두 문제:
- payload schema 오류 / corrupted JSON 처럼 **재시도해도 회복 불가능한** 메시지가 같은
  partition 의 후속 처리를 막는다 (poison pill).
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
- `interval=0` — CDC 의 실패는 통상 transient (idempotent retry 로 회복) 이거나 회복
  불가능한 schema 오류. 전자는 0ms 로도 풀리고, 후자는 wait 시간이 의미 없으므로 짧게
  끝낸다.
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

## 용어 풀이 (쉽게)

- **DLQ / DLT (Dead Letter Queue / Topic)** — 아무리 재시도해도 처리 안 되는 '문제 메시지'를 따로 모아두는 격리 보관함. 주소 불명 택배를 별도 창고에 두고 정상 처리는 계속 흐르게 한다.
- **poison pill(독이 든 메시지)** — 깨진 한 건이 줄 맨 앞에 박혀 뒤의 멀쩡한 메시지까지 다 막는 상황. 계산대 맨 앞 손님이 결제가 안 돼 뒷줄 전체가 멈추는 것.
- **backoff(백오프)** — 재시도 사이에 두는 대기 시간. 여기선 회복 가능한 건 0초로 바로, 안 되는 건 빨리 포기하도록 짧게 잡았다.
- **partition / offset** — Kafka가 메시지를 나눠 담는 칸이 partition, 그 안에서 '몇 번째 메시지'인지 표시가 offset. 책의 '권'과 '쪽 번호'.
- **consumer lag(컨슈머 지연)** — 들어온 메시지 대비 아직 못 읽고 밀려 있는 양. 한 건이 막히면 이 수치가 끝없이 치솟는다.
- **replay(재처리)** — 격리함의 실패 메시지를, 원인을 고친 뒤 원래 줄로 다시 돌려보내 처리하는 것.
- **ack(확인 응답)** — 컨슈머가 "이 메시지 잘 처리했음"이라고 도장을 찍는 것. ack를 안 하면 같은 메시지가 다시 온다.

## 다시 검토할 시점
- DLT 메시지가 일시적이지 않게 누적되면 별도 storage (DB/S3) 로 archive + UI 검토 도구 추가.
- 영구 schema 오류 비율이 높아지면 producer 측 schema 검증 (Schema Registry / Avro) 도입 검토.
- replay endpoint 의 보안 (현재는 보안 게이트 외부 의존) — service-account / OIDC 로 강화.
