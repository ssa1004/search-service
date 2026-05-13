// filter + facet + sort 조합 검색 시나리오 — query DSL 비용 측정.
//
// POST /api/v1/search/products — keyword 외에 filter (terms / range), facet (terms / range),
// sort 까지 모두 채운 풀 query. ES query DSL 빌더가 만드는 bool / aggregation tree 가
// 가장 무거운 경우다.
//
// 단순 검색의 절반 RPS 로 운용해 query DSL 비용을 격리해 본다 — 두 시나리오의 p95 차이가
// facet aggregation + filter cache miss 의 순수 비용에 가깝다.
//
// thresholds:
//   - http_req_duration p95 < 300ms — facet 비용을 감안한 느슨한 임계
//   - http_req_failed rate < 1%
//   - facet_compute_ms p95 < 200ms — 보조 지표 (server-side waiting 기준)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, BRANDS, PAGE_SIZES, PRICE_RANGES, randomQuery } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const facetCompute = new Trend('facet_compute_ms', true);

export const options = {
  scenarios: {
    filters: {
      executor: 'constant-arrival-rate',
      rate: 200,                 // 단순 검색의 절반 — query DSL 비용 격리
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 60,
      maxVUs: 250,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:search-with-filters}': ['p(95)<300', 'p(99)<600'],
    facet_compute_ms: ['p(95)<200'],
  },
};

const SORT_FIELDS = [
  { field: 'priceWon', direction: 'asc' },
  { field: 'priceWon', direction: 'desc' },
  { field: 'clickCount', direction: 'desc' },
  { field: 'releasedAt', direction: 'desc' },
];

function pickRandom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function buildBody() {
  const range = pickRandom(PRICE_RANGES);
  const sort = pickRandom(SORT_FIELDS);
  const size = pickRandom(PAGE_SIZES);
  // 1~3개 브랜드 무작위 (facet 의 cardinality 영향을 다양하게).
  const brandCount = 1 + Math.floor(Math.random() * 3);
  const brands = [];
  for (let i = 0; i < brandCount; i++) brands.push(pickRandom(BRANDS));

  return {
    keyword: randomQuery(),
    filters: [
      { field: 'brand', op: 'terms', values: brands },
      { field: 'priceWon', op: 'range', from: range.from, to: range.to,
        fromInclusive: true, toInclusive: false },
    ],
    facets: [
      { name: 'by-brand', field: 'brand', type: 'terms', size: 10 },
      { name: 'by-category', field: 'category', type: 'terms', size: 10 },
      { name: 'price-range', field: 'priceWon', type: 'range',
        buckets: [
          { key: '50k-100k', from: 50000, to: 100000 },
          { key: '100k-200k', from: 100000, to: 200000 },
          { key: '200k-300k', from: 200000, to: 300000 },
          { key: '300k+', from: 300000 },
        ] },
    ],
    sort: sort,
    page: 0,
    size: size,
  };
}

export default function () {
  const url = `${BASE_URL}/api/v1/search/products`;
  const body = JSON.stringify(buildBody());

  const res = http.post(url, body, {
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    tags: { name: 'search-with-filters' },
  });

  // server-side waiting 을 facet 계산 비용의 proxy 로 사용 — query DSL + agg + sort 가
  // 모두 들어간 풀 비용. http_req_duration 보다 network 변수 제거에 유리.
  facetCompute.add(res.timings.waiting);

  check(res, {
    'status 200': (r) => r.status === 200,
    'body has facets': (r) => (r.body || '').includes('facets') || (r.body || '').includes('totalHits'),
  });

  sleep(0.1);
}
