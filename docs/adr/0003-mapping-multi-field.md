# ADR-0003: ES Mapping 설계 — multi-field (text + keyword + autocomplete)

## 상태
적용

## 배경
한 필드 (예: 상품 이름) 가 여러 검색 시나리오에 동시에 사용된다.
- 일반 키워드 검색 — 형태소 분석 후 토큰 매칭 (`text`, standard analyzer).
- 정렬 / 정확 일치 / aggregation — 토큰화하지 않은 원본 (`keyword`).
- 자동완성 — prefix 가 1글자 부터 매칭 (`text`, edge_ngram analyzer).

매핑을 하나로 두면 셋 중 하나만 잘 작동한다. text 로 두면 정렬 / aggregation 이 안 되고
(fielddata 사용은 메모리 폭발 위험), keyword 로 두면 형태소 매칭이 안 된다.

## 결정
**ES 의 multi-field 기능으로 한 필드를 여러 형태로 동시 indexing.**

```json
"name": {
  "type": "text",
  "analyzer": "ko_standard",
  "fields": {
    "keyword":      { "type": "keyword", "ignore_above": 256 },
    "autocomplete": { "type": "text", "analyzer": "edge_ngram_index",
                       "search_analyzer": "edge_ngram_search" }
  }
}
```

검색 측에서는 용도별로 필드를 명시한다.
- 키워드 검색: `multi_match` 의 `fields: ["name^3", "brand^2", "name.autocomplete"]`
- 정렬: `sort.field = "name.keyword"`
- 자동완성: `match` 의 `field: "name.autocomplete"`

`brand` / `category` / `status` 는 단일 `keyword` — terms aggregation 과 정확 일치만 필요.

## 장단점
- 한 row 에 한 번만 indexing — 디스크 / 인덱싱 비용은 multi-field 만큼 비례 증가하지만
  query 비용은 실시간으로 절약된다.
- 운영 / 개발자가 매핑을 한 번만 보면 모든 검색 시나리오의 필드 사용처를 알 수 있다.
- analyzer 간 충돌 없음 — 각 sub-field 가 독립.
- mapping JSON 한 곳에서 관리되므로 변경이 alias-based reindex 로 자연스럽게 흘러간다
  (ADR-0005).

## 다시 검토할 시점
- 한국어 형태소 분석은 ADR-0015 에서 nori + user_dictionary 로 적용 — `ko_standard` 가 그
  결과를 반영한다.
- 자동완성을 ES `completion suggester` 로 옮길 때 (ADR-0007).
