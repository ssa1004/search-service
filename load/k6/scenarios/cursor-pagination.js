// cursor 기반 페이지네이션의 decoding 안정성 검증.
//
// GET /api/v1/search/products?q=...&cursor=... 로 한 검색 결과를 cursor 가 빌 때까지 끝까지
// 따라간다. ES 의 `search_after` 를 application 단에서 base64 cursor token 으로 감싼다고
// 가정 — 매 페이지마다 next_cursor 를 받고 다음 호출에 그대로 넣는다.
//
// 본 시나리오는 단순 throughput 측정이 아니라 *invariant 검증* 에 가깝다:
//   - cursor decoding 이 어느 페이지에서도 실패하면 안 된다 (decode_error == 0)
//   - 한 검색이 N 페이지로 끝나도록 next_cursor 가 null 로 닫혀야 한다 (무한루프 차단)
//   - 페이지마다 hits.length 가 size 이하여야 한다
//
// thresholds:
//   - cursor_decode_error count == 0 — invariant 위반은 0건이어야 한다
//   - cursor_missing_page count == 0 — 중간에 cursor null 인데 totalHits > 누적 hits
//   - http_req_failed rate < 1%

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, randomQuery } from '../lib/config.js';
import { authHeader } from '../lib/auth.js';

const decodeError = new Counter('cursor_decode_error');
const missingPage = new Counter('cursor_missing_page');
const pagesTraversed = new Trend('cursor_pages_per_query', false);
const lastPageLatency = new Trend('cursor_last_page_latency_ms', true);

const PAGE_SIZE = 20;
const MAX_PAGES = 50;  // 무한루프 안전망 — 50 페이지 (1000건) 까지만

export const options = {
  scenarios: {
    paginate: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '15s', target: 10 },
        { duration: '60s', target: 10 },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    cursor_decode_error: ['count==0'],
    cursor_missing_page: ['count==0'],
  },
};

function safeJsonParse(body) {
  try {
    return JSON.parse(body);
  } catch (_) {
    return null;
  }
}

export default function () {
  const q = randomQuery();
  let cursor = null;
  let totalSeen = 0;
  let pages = 0;
  let declaredTotal = null;
  let lastTook = 0;

  while (pages < MAX_PAGES) {
    const url = cursor
      ? `${BASE_URL}/api/v1/search/products?q=${encodeURIComponent(q)}&size=${PAGE_SIZE}&cursor=${encodeURIComponent(cursor)}`
      : `${BASE_URL}/api/v1/search/products?q=${encodeURIComponent(q)}&size=${PAGE_SIZE}`;

    const res = http.get(url, {
      headers: authHeader(),
      tags: { name: 'cursor-pagination' },
    });
    pages++;
    lastTook = res.timings.waiting;

    const ok = check(res, { 'status 200': (r) => r.status === 200 });
    if (!ok) {
      decodeError.add(1);
      break;
    }

    const json = safeJsonParse(res.body);
    if (!json) {
      decodeError.add(1);
      break;
    }

    if (declaredTotal === null && typeof json.totalHits === 'number') {
      declaredTotal = json.totalHits;
    }

    const hits = json.hits || [];
    if (!Array.isArray(hits) || hits.length > PAGE_SIZE) {
      decodeError.add(1);
      break;
    }
    totalSeen += hits.length;

    cursor = json.nextCursor || json.next_cursor || null;
    if (!cursor) {
      // 마지막 페이지 — invariant: 누적 hits 가 totalHits 와 일치해야 한다.
      // (declaredTotal 이 응답에 없는 구현이면 검증 skip.)
      if (declaredTotal !== null && totalSeen < declaredTotal && declaredTotal <= MAX_PAGES * PAGE_SIZE) {
        missingPage.add(1);
      }
      break;
    }
  }

  pagesTraversed.add(pages);
  lastPageLatency.add(lastTook);

  check(null, {
    'finished within max pages': () => pages < MAX_PAGES || cursor === null,
  });

  sleep(0.3);
}
