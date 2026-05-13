// 시나리오 공통 설정.
//
// BASE_URL 은 환경변수로 덮어쓸 수 있도록. 기본은 docker-compose 통합 환경의 노출 포트.
// search-service 는 bootRun / docker 둘 다 8080 으로 떨어진다.

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * 검색어 pool — 시나리오마다 같은 풀을 round-robin 한다.
 *
 * 시드 데이터의 brand / product name 과 겹치도록 nori 가 토큰 분해할 수 있는 한국어 + 영문
 * 혼합 키워드를 기본으로 둔다. realtime-feed-service 의 SKU pool 과 의도적으로 영역만
 * 다르게 구성 — orderbook 쪽이 ticker symbol 라면 search 쪽은 자유로운 검색어 문자열.
 *
 * `K6_QUERIES` env 로 CSV 주입 가능 — 부하 환경의 시드와 맞춰 갈아끼우기 위해.
 */
export const QUERIES = (__ENV.K6_QUERIES || 'Air Max,Jordan 1,Yeezy,New Balance,덩크로우,조던1,에어맥스,스니커즈,운동화,러닝화')
  .split(',')
  .map((s) => s.trim())
  .filter((s) => s.length > 0);

/**
 * VU 인덱스 기반 검색어 선택 — 같은 VU 는 항상 같은 검색어를 사용 (cache hit 비율 안정화).
 *
 * Round-robin 으로 분산해야 ES query cache / synonym cache 가 한쪽으로 쏠리지 않는다.
 */
export function pickQuery(vuId) {
  if (QUERIES.length === 0) return 'Air Max';
  return QUERIES[vuId % QUERIES.length];
}

/**
 * 무작위 검색어 — 동일 VU 라도 iteration 마다 다른 키워드를 쓰고 싶을 때.
 *
 * cursor pagination 처럼 한 query 를 끝까지 따라가는 시나리오 외에는 이 쪽이 자연스럽다.
 */
export function randomQuery() {
  if (QUERIES.length === 0) return 'Air Max';
  return QUERIES[Math.floor(Math.random() * QUERIES.length)];
}

/**
 * 검색 분야의 흔한 브랜드 facet 후보 — filter / facet 시나리오에서 재사용.
 */
export const BRANDS = ['Nike', 'Adidas', 'Jordan', 'New Balance', 'Puma'];

/**
 * 가격대 (원) — range filter / range facet 의 후보값.
 */
export const PRICE_RANGES = [
  { from: 50000, to: 100000 },
  { from: 100000, to: 200000 },
  { from: 200000, to: 300000 },
  { from: 300000, to: 500000 },
];

/**
 * 일반적인 page size 후보 — pagination 부하의 다양성.
 */
export const PAGE_SIZES = [10, 20, 50];
