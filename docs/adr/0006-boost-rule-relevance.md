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

## 다시 검토할 시점
- click 데이터가 충분히 누적되어 conversion rate / dwell time 같은 더 정교한 시그널을
  쓸 때.
- 도메인 PM 이 "특정 카테고리 / 브랜드 promotion" 같은 사람 결정을 ranking 에 넣고 싶어
  할 때 — function_score 의 추가 함수로 표현.
- ML 랭커 (LTR) 도입 — function_score 출력을 feature 로 학습 모델에 통합.
