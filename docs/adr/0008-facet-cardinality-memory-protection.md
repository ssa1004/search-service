# ADR-0008: facet aggregation 의 cardinality 제한 + 메모리 보호

## 상태
적용

## 배경
faceted filter (브랜드별 / 가격대별 분포) 는 ES `terms` / `range` aggregation 으로 구현된다.
naive 한 사용은 운영에서 ES 노드를 죽인다.

문제 케이스:
- `terms aggregation` 에 `size: 1000000` 을 넣어 모든 브랜드 분포를 한 번에 받으려 한다 →
  ES 가 1M bucket 메모리 할당 후 응답.
- 사용자가 입력한 facet 필드명을 그대로 받아 `text` 필드에 aggregation → fielddata 로
  inverted index 가 메모리에 로드되어 노드 폭발.
- 한 검색 요청에 facet 10개를 동시 요청 → 각 aggregation 의 비용이 합산되어 응답
  지연.

## 결정
**도메인 객체 (`FacetSpec`) 가 cardinality / 사용 가능 필드를 강제. 검색 단계에서 거부 가능.**

- `FacetSpec.Terms.size` 상한 = 100. 그 이상은 운영 의미가 없음 (UI 도 100 개 brand 를
  보여주지 않음).
- `FacetSpec.Range.buckets` 는 사용자가 명시적으로 정의한 구간만 — 자동 분포 (auto
  histogram) 는 미허용.
- aggregation 가능 필드는 매핑에서 `keyword` 또는 numeric 으로 제한 (mapping 자체가
  방어). `text` 필드의 fielddata 사용은 매핑 단계에서 비활성 (default).
- 한 요청의 facet 수도 use case 단에서 sane 한 상한 권장 (현재 10개 — REST DTO 의
  `@Size`).

cardinality 가 진짜로 큰 차원 (예: product 의 ID 자체) 에 대한 distinct count 가 필요하면
`cardinality` aggregation 의 HyperLogLog precision 을 명시적으로 낮춰 사용 (별도 endpoint
로 분리, 본 검색 흐름과 무관).

## 장단점
- 사용자 / 클라이언트가 size=1M 같은 위험한 값을 보내도 도메인 단계에서 차단 — ES 까지
  전달되지 않음.
- 매핑 차원에서도 보호 (text 필드 fielddata 비활성) — 누군가 우회해도 ES 가 거부.
- 일부 use case (대시보드의 전체 분포) 가 100 bucket 으로 제한될 수 있음 — 별도 endpoint
  로 운영 (예: scroll + pagination).

## 다시 검토할 시점
- 운영 데이터 분포가 변해 한 차원의 자연 cardinality 가 100 을 넘어 사용자에게 보여주는
  것이 의미 있어질 때 — `FacetSpec.Terms.MAX_SIZE` 조정.
- aggregation 비용이 search 비용을 압도하는 패턴이 보일 때 — 분리 endpoint 로 빼고
  background pre-aggregation 으로 캐싱.
