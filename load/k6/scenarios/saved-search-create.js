// 저장 검색 (Saved Search) 등록 write throughput 시나리오.
//
// POST /api/v1/saved-searches — 사용자가 검색 조건을 저장. 5분 주기 스케줄러가 cursor
// 이후 신규 매치를 평가해 Kafka `search.alert.fired` 로 발행하는 경로의 진입점.
// 검색 read 경로와 달리 SavedSearchRepository (JPA) 에 INSERT 가 들어가는 쓰기 시나리오라
// DB connection pool / 트랜잭션 비용이 더 두드러진다.
//
// VU 를 0 → 50 까지 ramping — 짧은 시간에 많은 사용자가 동시에 alert 를 거는 트래픽
// (예: 한정판 release 직전) 을 흉내낸다.
//
// thresholds:
//   - http_req_duration p95 < 150ms — write 라 read 보다 임계 느슨
//   - http_req_failed rate < 1%
//   - saved_search_created (Counter) > 0 — 한 건이라도 성공

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, BRANDS, PRICE_RANGES, randomQuery } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const created = new Counter('saved_search_created');

export const options = {
  scenarios: {
    ramp_create: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '10s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:saved-search-create}': ['p(95)<150', 'p(99)<400'],
    saved_search_created: ['count>0'],
  },
};

const CHANNELS = ['EMAIL', 'PUSH', 'SLACK'];

function pickRandom(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function buildBody(vuId, iter) {
  const range = pickRandom(PRICE_RANGES);
  return {
    // VU + iteration 조합으로 유저 ID 의 multiplicity 를 만든다 — 같은 user 가 여러 alert.
    userId: `k6-user-${vuId % 100}`,
    keyword: randomQuery(),
    filters: [
      { field: 'brand', op: 'terms', values: [pickRandom(BRANDS)] },
      { field: 'priceWon', op: 'range', from: range.from, to: range.to,
        fromInclusive: true, toInclusive: false },
    ],
    notifyChannel: pickRandom(CHANNELS),
    // alert 이름 — VU + iter 로 유니크. 도메인 invariant 가 사용자별 unique name 인지에
    // 따라 충돌 가능 — 충돌 시 4xx 로 떨어지면 thresholds 에서 잡힌다.
    name: `k6-alert-${vuId}-${iter}-${Date.now()}`,
  };
}

export default function () {
  const url = `${BASE_URL}/api/v1/saved-searches`;
  const body = JSON.stringify(buildBody(__VU, __ITER));

  const res = http.post(url, body, {
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    tags: { name: 'saved-search-create' },
  });

  const ok = check(res, {
    'status 201 or 200': (r) => r.status === 201 || r.status === 200,
    'response has id': (r) => {
      const b = r.body || '';
      return b.includes('id') || b.includes('savedSearchId');
    },
  });
  if (ok) created.add(1);

  sleep(0.2);
}
