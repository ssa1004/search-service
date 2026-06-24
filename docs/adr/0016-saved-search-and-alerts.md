# ADR-0016: 저장 검색 (Saved Search) + 알림

## 상태
적용

## 배경

검색은 *지금 보고 있는* 정보가 전부가 아니다. 사용자가 자주 찾는 query — 예: "특정 모델
한정판 사이즈 270, 가격 30만원 이하" — 가 *나중에* 새로 등록되면 그 시점에 알림을 받고
싶다. 이 기능 없이는 사용자가 매일 같은 query 를 수동으로 입력해야 하고, 신규 등록
순간을 놓치면 인기 매물은 이미 사라져 있다.

해결책은 **검색을 저장 → 주기적으로 재실행 → 신규 매치 시 알림**.

## 결정

### 도메인

`SavedSearch` aggregate:

```
ownerId           (RecipientId — 알림 받을 주체)
query             (SearchQuery — 검색 조건 그대로 직렬화)
notifyChannel     (PUSH / EMAIL / IN_APP / WEBHOOK)
createdAt
lastEvaluatedAt   (마지막 평가 시각 — 다음 사이클에서 cursor 로 사용)
evaluationCursor  (마지막으로 본 product id — 그 이후만 매치)
status            (ACTIVE / PAUSED / DELETED)
```

`evaluationCursor` 가 핵심 — 매번 *전체 결과* 를 다시 보지 않고 *cursor 이후 신규* 만
평가. ES `range filter` 또는 cursor pagination 으로 효율적 query.

### 평가 스케줄러

`SavedSearchEvaluatorJob` — 5분마다 실행.

흐름:

1. `repository.findActiveBatchAfter(cursor, BATCH_SIZE)` — 100개씩 cursor 기반 batch 로딩
2. 각 SavedSearch 별로 `matchFinder.findNew(saved, evaluationCursor, MAX_MATCHES_PER_SEARCH)` — ES 에서 cursor 이후 매치 조회 (최대 50개)
3. 매치 있으면 `alertPublisher.publish(SavedSearchAlert)` — Kafka topic `search.alert.fired` 에 발행 (다른 service 가 consume — notification-hub 등)
4. SavedSearch 의 `lastEvaluatedAt` + `evaluationCursor` 갱신
5. 다음 batch 가 BATCH_SIZE 보다 적으면 종료

### 멀티 인스턴스 안전 — ShedLock

스케줄러는 ShedLock 으로 *한 인스턴스만* 실행 보장. 두 인스턴스가 동시에 같은 SavedSearch
를 평가하면 알림 중복 + lastEvaluatedAt race.

```kotlin
@Scheduled(fixedDelayString = "\${search.savedsearch.evaluate-interval-ms:300000}")
@SchedulerLock(name = "savedsearch-evaluator", lockAtMostFor = "4m", lockAtLeastFor = "1m")
fun run() { ... }
```

### 사용자당 한도 — 무한 등록 방지

한 사용자 최대 50개 SavedSearch. 이 한도가 없으면:
- 악의적 사용자가 수천 개 등록 → 매 평가 사이클이 폭주
- 알림 폭탄 — 한 신규 product 가 수십 개 SavedSearch 에 동시 매치되어 알림 N개

`SaveSearchUseCase` 가 등록 전 `repository.countByOwner(ownerId) >= 50` 검증.

### 알림 채널 분리 — Outbox 패턴

`alertPublisher.publish(SavedSearchAlert)` 는 *Kafka 발행만* — 실제 알림 전송 (push / email)
은 별도 service (notification-hub 등) 가 consume. 책임 분리:
- search-service — 매치 감지 + alert 이벤트 발행
- notification-hub — 채널 별 발송 + retry / DLQ

이 경계가 분명해서 search-service 가 알림 채널 추가 (예: SMS, KakaoTalk) 시 변경 X.

## 대안

### push 기반 (CDC + 새 product 마다 모든 SavedSearch 매치 검사)
탈락 — 신규 product 가 *매분 수천 건* 등록되는 도메인에서 매번 모든 SavedSearch (수만 건)
를 평가하는 비용이 큼. pull 기반 (스케줄러 polling) 은 batch 처리 + cursor 로 부하 분산
가능.

### 스케줄러 주기 1분
탈락 — 1분 주기는 알림 latency 는 좋지만 ES query 부하가 5배. 5분 주기 + 사용자 expectation
(즉시 알림 X — *신상 알림* 정도) 매칭이 합리적. 후속에 *프리미엄 사용자* 1분 옵션 검토.

### Webhook 기반 외부 통합
검토 — 사용자가 자기 webhook URL 등록 → search-service 가 직접 HTTP POST. 본 ADR 의 알림
채널 한 종류로 추가 가능. 보안 (HMAC 서명) 필요.

## 결과

- 사용자가 검색을 *지속적으로 모니터링* 가능 — 신규 매치 시 알림
- search-service 가 알림 발송 책임에서 분리됨 (Outbox + 외부 service consume)
- 멀티 인스턴스 안전 (ShedLock)
- 사용자당 한도 + batch 처리로 부하 폭주 차단
- (단점) 알림 latency 최소 5분 — 즉시성이 핵심인 경우 별도 path 필요
- (단점) `evaluationCursor` 기반 — product 가 *재인덱싱* 으로 cursor 가 흔들리면 일부 알림
  중복 / 누락 가능. 후속 ADR 에서 cursor 안정화 (예: timestamp + tie-break id)

## 용어 풀이 (쉽게)

- **저장 검색(Saved Search)** — 자주 쓰는 검색 조건을 저장해 두면, 나중에 조건에 맞는 새 상품이 올라올 때 알림을 받는 기능. '이 조건 뜨면 알려줘' 예약.
- **aggregate(애그리거트)** — 함께 묶여 다뤄지는 한 덩어리 데이터(여기선 저장 검색 한 건의 모든 정보). 도메인에서 '하나의 단위로 저장·수정하는 묶음'.
- **cursor(커서) 기반 평가** — 매번 전체를 다시 보지 않고, '지난번 마지막으로 본 지점' 표시(cursor) 이후 새로 생긴 것만 보는 방식. 읽던 책에 끼워 둔 책갈피.
- **pull vs push** — push는 새 상품마다 모든 저장 검색을 즉시 다 검사(부하 큼), pull은 스케줄러가 주기적으로 모아서 확인(부하 분산). 여기선 pull을 택했다.
- **ShedLock / 멀티 인스턴스** — 같은 스케줄을 여러 서버가 동시에 돌려 알림이 중복되지 않게, 한 대만 실행하도록 DB 잠금을 거는 도구.
- **Outbox(아웃박스) 경계** — search-service는 '알림 이벤트'만 Kafka에 발행하고 실제 발송은 다른 서비스가 맡는 책임 분리. 우체통에 넣으면 집배원이 따로 배달.
- **HMAC 서명** — webhook으로 보낼 때 '이게 진짜 우리가 보낸 게 맞다'는 위조 방지 도장. 비밀 키로 메시지에 봉인을 찍는 것.

## 후속

- ADR (예정): 프리미엄 사용자 1분 주기 옵션
- ADR (예정): 알림 빈도 조절 (한 사용자 알림 폭주 방지 — 최근 1시간 N회 이상이면 통합)
- ADR (예정): SavedSearch 의 자동 만료 — 90일 inactive 면 PAUSED
