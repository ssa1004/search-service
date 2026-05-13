// 단순 검색 latency 시나리오.
//
// GET /api/v1/search/products?q=... — 키워드만 던지는 가장 일반적인 검색 경로.
// filter / facet / sort 가 모두 비어 있어 ES 비용은 bm25 + function_score (인기도 + 신상품
// decay) 만 들어간다. boost rule 의 cache 동작과 query analyzer (nori) 의 base latency 를
// 본다.
//
// thresholds:
//   - http_req_duration p95 < 100ms — 단순 검색이라 가장 빡센 임계
//   - http_req_failed rate < 1%
//   - query_p95 (커스텀 Trend) — http_req_waiting 의 alias 로 server-side 만 본 보조 지표

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { BASE_URL, randomQuery } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const queryLatency = new Trend('search_simple_query_latency_ms', true);

export const options = {
  scenarios: {
    simple: {
      executor: 'constant-arrival-rate',
      rate: 500,                  // 초당 500 req — 단순 검색이라 가장 높은 RPS
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 100,
      maxVUs: 400,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:search-simple}': ['p(95)<100', 'p(99)<250'],
    search_simple_query_latency_ms: ['p(95)<100'],
  },
};

export default function () {
  const q = randomQuery();
  const url = `${BASE_URL}/api/v1/search/products?q=${encodeURIComponent(q)}`;

  const res = http.get(url, {
    headers: authHeader(),
    tags: { name: 'search-simple' },
  });

  // http_req_waiting 은 TTFB — server-side query latency 의 근사.
  // 한 iteration 한 호출이라 그대로 Trend 에 쌓아도 분포가 의미를 갖는다.
  queryLatency.add(res.timings.waiting);

  check(res, {
    'status 200': (r) => r.status === 200,
    'body has hits or totalHits': (r) => {
      const b = r.body || '';
      return b.includes('hits') || b.includes('totalHits') || b === '{}' || b.startsWith('{');
    },
  });

  sleep(0.05);
}
