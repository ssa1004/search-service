# ADR-0006: boost rule + relevance tuning — function_score, click-through rate

## 상태
적용

## 배경
키워드 매칭의 default 점수 (BM25) 만으로 정렬하면 인기 상품 / 신상품이 묻히고, 키워드와
가장 잘 매칭되는 (그러나 재고 없거나 오래된) 상품이 위로 올라가는 경우가 잦다. commerce
도메인은 사용자가 클릭 / 구매할 가능성이 높은 상품을 위에 놓는 게 매출에 직결된다.

학습된 ML 랭커 (LTR) 가 정답이지만 초기 운영에서는 단순한 규칙 두 개로 충분히 큰 개선
가능 — **인기도 (clickCount) + 신상품 (출시일 decay)**.

## 결정
**ES `function_score` 로 BM25 점수에 두 함수의 결과를 곱한다.**

1. **인기도 — `field_value_factor` (modifier `log1p`)**:
   `log(1 + clickCount) * popularityWeight`. log 함수라 클릭 수 폭증해도 점수는 안정적
   증가 (선형 가중은 인기 상품이 너무 압도해 다양성 손실).

2. **신상품 — `gauss decay`**: 출시일 origin 기준 반감기 (`freshnessHalfLife`) 만큼
   지나면 0.5, 두 배 지나면 0.25. 운영 default 30일 — 한정판 / 신상품 위주 commerce
   도메인은 발매 직후 1-2주 가 핵심 트래픽이라는 가정.

`BoostRule` 도메인 객체가 weight / 반감기의 sane range 를 강제 (`popularityWeight ≤ 10`,
`freshnessHalfLife ∈ 1d..365d`) — 누군가 weight=10000 을 넣어 점수를 망가뜨리는 사고
방지.

학습 시그널 — **click-through rate (CTR)**:
- 사용자가 검색 결과에서 상품을 클릭하면 `RecordSearchClickService` 가 `search_clicks`
  테이블에 기록하고 ES 의 해당 product `clickCount += 1` (painless partial update).
- 다음 검색 시 function_score 가 즉시 새 클릭 시그널을 반영.

## 장단점
- 운영 룰이 두 함수만 — 디버깅 / 설명 가능. weight 조정만으로 정책 변경.
- 인기도 / 신상품 모두 ES 안에서 끝나므로 추가 latency 거의 없음.
- 실시간 학습 — 클릭 발생 → 다음 검색에 즉시 반영.
- click farm / bot 트래픽이 ranking 을 오염시킬 수 있음 — 운영에서 click 의 사용자 분포
  / IP 다양성 검사로 후처리 필요.
- 초기 trending 상품은 클릭 0 인 상태에서 시작 — cold-start 문제. 신상품 boost 가 어느
  정도 커버.

## 용어 풀이 (쉽게)

- **BM25** — 검색어와 문서가 얼마나 잘 맞는지를 ES가 기본으로 매기는 점수 공식. 글자 매칭만 보고 인기·신상은 모른다.
- **function_score** — 그 기본 점수에 '추가 점수'를 곱해 인기 상품·신상품을 위로 끌어올리는 ES 기능. 이름만 맞는 곳 대신 잘 팔리는 걸 위로.
- **boost(부스트)** — 특정 조건에 점수를 더 얹어 순위를 끌어올리는 것. '신상이면 가산점' 같은 가중치.
- **decay / 반감기(half-life)** — 시간이 지날수록 점수를 서서히 깎는 것. 반감기 30일이면 30일 지나 점수 절반, 60일 지나 4분의 1. 방사능 반감기와 같은 개념.
- **log1p / 로그 가중** — 클릭 수가 폭증해도 점수가 무한정 안 커지게 완만하게 눌러 주는 수학 함수. 인기 상품 하나가 화면을 독차지하는 걸 막는다.
- **CTR (Click-Through Rate, 클릭 전환율)** — 결과를 보여줬을 때 실제로 클릭된 비율. 검색 결과가 쓸모 있었는지 보는 지표.
- **cold-start(콜드 스타트)** — 갓 나온 신상품은 클릭 기록이 0이라 인기 점수를 못 받는 출발선 문제. 신상품 가산점으로 보완한다.
- **LTR (Learning to Rank, 학습형 랭커)** — 규칙 대신 클릭 데이터로 'AI가 직접 순위 매기는 법'을 학습하는 더 정교한 방식. 여기선 아직 안 쓰고 단순 규칙으로 시작.

## 다시 검토할 시점
- click 데이터가 충분히 누적되어 conversion rate / dwell time 같은 더 정교한 시그널을
  쓸 때.
- 도메인 PM 이 "특정 카테고리 / 브랜드 promotion" 같은 사람 결정을 ranking 에 넣고 싶어
  할 때 — function_score 의 추가 함수로 표현.
- ML 랭커 (LTR) 도입 — function_score 출력을 feature 로 학습 모델에 통합.
