# ADR-0017: 운영자 동의어 사전 + ES synonym graph 런타임 reload

## 상태
적용

## 배경

검색 키워드는 운영자 의도와 사용자 표현이 자주 어긋난다.

- "조던1" 으로 검색하면 정식 표기 "에어 조던 1" 색인은 매칭 실패 — recall 손해.
- "Air Jordan 1" 영문 표기, "에어조던1" 붙여 쓴 표기, "AJ1" 약자 모두 같은 상품군이지만 토큰이
  다 다르다.
- ADR-0015 의 nori user_dictionary 가 일부를 보완하지만 user_dictionary 는 *형태소 분해*
  단계의 사전이라 *동의 관계* 표현은 못 한다 (조던1 과 에어조던1 이 같은 의미라는 정보를
  분해 사전으로는 표현 불가).

운영팀은 검색 zero-result 로그를 보고 신조어 / 약자 / 오타 패턴이 발견되면 *즉시* 동의어를
추가하고 *재시작 없이* 반영하길 원한다.

## 결정

### 동의어 그룹 도메인 + RDB 진실값

```
SynonymGroup
  id          (UUID)
  terms       List<String>
  direction   BIDIRECTIONAL | ONE_WAY
  updatedAt   Instant
  updatedBy   String     ← 운영자 audit
```

운영자가 등록 / 삭제하는 단위는 *그룹*. 한 그룹 안의 term 들이 서로 동의 관계.

- BIDIRECTIONAL — `[조던1, 에어조던1, 에어 조던 1]` → 어느 표기로 검색해도 셋 다 매칭.
- ONE_WAY — `[AJ1, 에어 조던 1]` → "AJ1" 검색 시 "에어 조던 1" 로 확장하지만 역방향 X.
  (오타 / 약자 → 정식 표기 일방향 매핑)

ES 의 synonym graph filter 는 inline 으로 rule 을 받지만 *진실값은 RDB* 에 둔다 — 인덱스
재생성 / reindex 시 RDB 의 모든 그룹을 다시 settings 에 밀어 넣어야 하기 때문. ES 만 들고
있으면 인덱스가 사라지면 동의어도 같이 사라진다.

### 등록 / 삭제 / 적용 분리

운영자 흐름:

1. `POST /api/v1/admin/synonyms` — 그룹 한 건 등록 (RDB 만).
2. `DELETE /api/v1/admin/synonyms/{id}` — 그룹 삭제 (RDB 만).
3. `POST /api/v1/admin/synonyms/apply` — 한 번에 ES 에 reload.

등록 / 삭제 즉시 ES 반영하지 않는 이유:
- ES synonym filter 는 *non-updateable setting* — 변경하려면 인덱스를 close → settings PUT →
  open 해야 한다. 매 등록마다 호출하면 검색 차단 시간이 누적된다.
- 운영자는 보통 여러 그룹을 한꺼번에 정리한 뒤 한 번 적용.
- 반대로 등록과 동시에 ES 자동 reload 가 필요하면 후속 ADR 에서 `@TransactionalEventListener`
  로 묶어 ES 호출 batching 가능.

### ES `synonym_graph` filter + ko_standard analyzer

```json
"filter": {
  "domain_synonyms": {
    "type": "synonym_graph",
    "synonyms": []
  }
},
"analyzer": {
  "ko_standard": {
    "tokenizer": "nori_user_dict",
    "filter": [
      "lowercase",
      "asciifolding",
      "nori_part_of_speech",
      "nori_readingform",
      "domain_synonyms"      ← 추가
    ]
  }
}
```

- `synonym_graph` 를 `synonym` 대신 사용 — multi-word synonym ("Air Jordan 1") 도 한 위치에
  여러 토큰으로 emit 해 phrase / proximity query 에 안전. ES 공식 권장.
- 신규 인덱스는 빈 배열로 시작 — 첫 `apply` 호출 시 RDB 의 그룹으로 채워짐.
- 기존 nori filter 와 `domain_synonyms` 의 순서는 *nori 분석 후* — 형태소 분해 결과 토큰에
  대해 동의어를 적용. (반대로 분해 전이면 user_dictionary 와 충돌해 "조던1" 이 분해된 뒤
  동의어를 못 잡는 사례.)

### Reload — close → settings PUT → open

```kotlin
client.indices().close { c -> c.index(physical) }
try {
    client.indices().putSettings { p ->
        p.index(physical).withJson(StringReader(settingsJson))
    }
} finally {
    client.indices().open { o -> o.index(physical) }
}
```

`finally` 로 open 보장 — settings PUT 실패 시에도 인덱스를 닫힌 채 두면 운영이 망가진다.
close 시간 동안 검색이 차단되지만 설정이 작아 보통 1~2 초.

대상 인덱스는 alias 가 아니라 *물리 인덱스* — alias 는 settings PUT 의 ambiguous 대상이라
8.x 가 거부하는 경우가 있다. `getAlias` 로 현재 매핑된 물리 이름을 찾아 PUT.

### Term validation — 도메인 단계 차단

ES synonym 표현은 쉼표 / 화살표 / 백슬래시가 구분자다. term 안에 그 문자가 있으면 표현이
깨진다 (`"a, b" => "c"` 같은 입력이 들어오면 ES 측에서 unexpected). 도메인 record 의
constructor 에서 정규식으로 차단 — REST 단계에서도 catch 되어 400 으로 반환.

term 길이 100자 / 그룹당 term 50개 cap — ES 분석 비용 / 운영자 가독성 가이드. 더 큰 그룹은
보통 잘못 묶은 것 (서로 다른 의미의 term 들이 한 그룹).

## 대안

### user_dictionary 만으로 해결
탈락 — user_dictionary 는 형태소 분해 사전이라 *동의 관계* 표현 불가. "조던1" 을 한 토큰으로
보존하는 것까지는 되지만 "조던1 ↔ 에어 조던 1" 매핑은 별도 메커니즘 필요.

### 매 등록마다 ES auto-reload
검토 → 후속. 운영자 편의성은 더 좋지만 close → open 빈도 증가가 검색 가용성에 영향. 운영 규모가
커지면 transactional outbox 로 *큐잉 후 batch reload* 패턴이 자연스럽다.

### 새 인덱스 + alias swap (zero-downtime)
검토 → 운영 데이터 양이 커지면. ADR-0005 의 reindex 흐름과 결합해 settings 변경마다 새 물리
인덱스를 만들고 alias 를 swap 하는 방식이 무중단이지만 수십~수백 GB 인덱스를 그때마다 복제
하는 비용이 크다. 동의어만 변경되는 경우엔 close → open 이 상대적으로 가볍다.

### S3 sync 기반 외부 사전 파일
검토 → user_dictionary 는 그 패턴을 따를 수 있지만 synonym 은 ES 가 cluster-wide settings 로
잡고 있어 파일 경로 + sync 의 운영 부담이 RDB + apply 보다 크다.

## 결과

- 운영자가 검색 신조어 / 약자 / 오타를 발견 즉시 추가 후 *재시작 없이* 적용.
- RDB 가 진실값 — 인덱스 재생성 시에도 동의어 보존.
- 도메인 단계의 term validation 으로 ES 표현 깨짐 / injection 차단.
- (단점) `apply` 호출 시 인덱스 close 시간 동안 검색 차단 — 운영 시간 외 호출 권장.
- (단점) 운영자가 `apply` 호출을 잊으면 RDB 와 ES 가 어긋남 — 운영 화면에 "n개 반영 대기"
  배지 + reminder 가 후속 작업.

## 용어 풀이 (쉽게)

- **synonym(동의어) graph** — '조던1 = 에어조던1 = Air Jordan 1'처럼 같은 뜻 단어를 한 묶음으로 등록해, 어느 표기로 검색해도 다 찾게 하는 것. graph 버전은 'Air Jordan 1'처럼 여러 단어로 된 동의어도 안전하게 다룬다.
- **런타임 reload(재시작 없는 반영)** — 서버를 껐다 켜지 않고도 동의어 같은 설정을 그 자리에서 갈아끼워 즉시 반영하는 것.
- **non-updateable setting / close→open** — ES 동의어 설정은 살아 있는 인덱스에서 바로 못 바꾼다. 그래서 인덱스를 잠깐 '닫고→설정 바꾸고→다시 여는' 절차로 교체한다(닫힌 1~2초 동안만 검색 중단).
- **BIDIRECTIONAL vs ONE_WAY(양방향·일방향)** — 양방향은 묶음 안 단어가 서로 다 통함, 일방향은 'AJ1→에어 조던 1'처럼 한쪽으로만 확장(약자·오타를 정식 표기로만 연결).
- **진실값(source of truth) = RDB** — 동의어의 '진짜 원본'은 DB에 둔다. 인덱스를 새로 만들어도 DB에서 다시 밀어 넣으면 동의어가 보존된다.
- **zero-result(0건 검색)** — 검색 결과가 하나도 안 나온 검색. 오타·다른 표기 때문일 때가 많아 동의어 보강의 단서가 된다.
- **injection(주입 공격)** — 입력에 특수문자(쉼표·화살표 등)를 섞어 시스템 표현을 깨거나 조작하려는 시도. 도메인 단계에서 그런 문자를 막아 차단한다.

## 후속

- ADR (예정): `@TransactionalEventListener` + 큐잉 으로 등록과 reload 묶음 처리
- ADR (예정): synonym 변경 audit log 별도 테이블 (rollback 지원)
- ADR (예정): zero-result query 로그와 cross — "이번 주 zero-result top 10 → 동의어 추천"
