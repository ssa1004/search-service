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

## 용어 풀이 (쉽게)

- **multi-field(멀티필드)** — 한 필드(예: 상품 이름)를 용도별로 여러 형태로 동시에 색인하는 것. 같은 재료를 회·구이·탕 세 가지로 미리 손질해 두는 셈.
- **형태소 분석 / analyzer** — 문장을 검색하기 좋은 '의미 단위'로 쪼개는 작업. '나이키의'에서 조사 '의'를 떼어 '나이키'로 만들어 매칭되게 한다.
- **text vs keyword** — text는 잘게 쪼개 검색용으로, keyword는 통째로(원본 그대로) 둬 정렬·정확 일치·집계용으로 쓰는 두 저장 방식.
- **edge_ngram(엣지 엔그램)** — 단어를 앞에서부터 한 글자씩 미리 잘라('A','Ai','Air'…) 색인. 'Air'만 쳐도 'Air Max'가 바로 뜨는 자동완성용.
- **aggregation(집계)** — 결과를 '브랜드별 개수'처럼 그룹지어 세는 것. 엑셀 피벗 테이블 같은 통계 묶음.
- **fielddata** — text 필드로 정렬·집계하려 할 때 ES가 통째로 메모리에 올리는 무거운 구조. 잘못 쓰면 메모리가 터져서 보통 꺼 둔다.

## 다시 검토할 시점
- 한국어 형태소 분석은 ADR-0015 에서 nori + user_dictionary 로 적용 — `ko_standard` 가 그
  결과를 반영한다.
- 자동완성을 ES `completion suggester` 로 옮길 때 (ADR-0007).
