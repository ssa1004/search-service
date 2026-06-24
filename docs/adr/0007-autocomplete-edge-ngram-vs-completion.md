# ADR-0007: 자동완성 — edge_ngram vs completion suggester

## 상태
적용

## 배경
ES 의 자동완성 구현은 두 가지 표준이 있다.

1. **completion suggester** — 전용 자료구조 (FST: finite-state transducer) 에 prefix 후보를
   저장. 매우 빠름 (μs 단위), 메모리 상에 항상 로드. weight 필드로 우선순위 지정 가능.
2. **edge_ngram analyzer** — 일반 inverted index 에 prefix 토큰 (1글자, 2글자, ..., N글자)
   을 미리 만들어 두고 일반 search query 로 매칭.

두 방식의 성능 / 표현력 차이가 운영 결정의 핵심.

## 결정
**edge_ngram analyzer 를 채택. completion suggester 는 미사용.**

근거:
- 자동완성 결과에도 일반 검색의 boost rule (인기도 + 신상품 + filter) 을 그대로 적용해야
  한다. completion suggester 는 weight 한 차원만 받고 function_score / aggregation 적용
  불가.
- 자동완성 결과를 검색 결과와 같은 `function_score` 로 ranking — 일관된 사용자 경험.
- prefix 매칭에 fuzzy / synonym 같은 ES 분석기 기능 활용 가능.

매핑 (`name.autocomplete`):
```json
"autocomplete": {
  "type": "text",
  "analyzer": "edge_ngram_index",     // indexing: edge_ngram 1-10
  "search_analyzer": "edge_ngram_search"  // query: lowercase 만 (full prefix)
}
```

`min_gram=1, max_gram=10` — 1글자 부터 매칭 (한글 자음 입력도 cover). max 10 은 디스크
용량 / 인덱싱 비용의 실용적 상한.

## 장단점
- 검색과 같은 query DSL 사용 — 학습 / 유지보수 코드 한 가지.
- function_score / filter / aggregation 모두 사용 가능 — completion suggester 의 한계 극복.
- indexing 비용 / 디스크 사용량은 completion suggester 보다 많음 (각 prefix 가 별도
  토큰).
- query 응답 시간은 완성 suggester (μs) 보다 느림 (수 ms) — 자동완성 빈도가 매우 높은
  대규모 트래픽 (초당 수만 호출) 에서는 차이가 눈에 띌 수 있음.

## 용어 풀이 (쉽게)

- **자동완성(completion) 두 방식** — 검색창에 글자를 칠 때마다 후보를 띄우는 기능. ES는 이를 만드는 길이 두 가지 있다(아래 둘).
- **completion suggester / FST** — 자동완성 전용 자료구조에 후보를 미리 넣어 두는 방식. 마이크로초 단위로 매우 빠르지만, 점수 규칙(인기·신상)은 못 얹는다.
- **edge_ngram(엣지 엔그램)** — 단어를 앞에서부터 한 글자씩 미리 잘라('A','Ai','Air'…) 일반 색인에 넣는 방식. 살짝 느리지만 인기·신상 점수와 필터를 그대로 쓸 수 있다.
- **inverted index(역색인)** — '어떤 단어가 어느 문서에 있나'를 거꾸로 정리한 검색의 핵심 표. 책 뒤의 '찾아보기(색인)'와 같다.
- **fuzzy(퍼지)** — 오타가 한두 글자 있어도 비슷하면 찾아 주는 너그러운 매칭('나이크'→'나이키').
- **초성 검색 / Hangul Jamo** — 'ㄴㅇㅋ'처럼 자음만 쳐도 '나이키'를 찾게 하는 한글 전용 기능.

## 다시 검토할 시점
- 자동완성 호출량이 기존 검색 트래픽의 10배를 넘어 ES CPU 가 자동완성에만 집중될 때 —
  completion suggester 를 보조 인덱스로 추가하고 boost rule 은 별도 후처리로 적용.
- 한국어 자동완성에서 자음 / 모음 분리 (초성 검색) 가 필요할 때 — edge_ngram 위에
  Hangul Jamo filter 를 더해 실현 가능.
