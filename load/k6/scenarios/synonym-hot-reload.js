// 동의어 사전 reload 직후 검색 latency spike 측정.
//
// 검증 대상: 운영자가 동의어 그룹을 등록 / 변경하고 `/api/v1/admin/synonyms/apply` 를
// 호출하면 ES settings update + alias swap 으로 무중단 적용된다 (ADR-0017). 적용 직후의
// 첫 검색 호출은 새 analyzer chain 으로 query 가 재구성되기 때문에 cache invalidation 의
// 비용이 가장 크다. 평소 대비 latency 가 얼마나 튀는지를 본다.
//
// 시나리오 구조:
//   1) baseline — 검색 N 회 호출해 평균 / p95 측정 (cache warm)
//   2) apply — admin/synonyms/apply 호출
//   3) post-apply — 검색 M 회 호출, 첫 호출의 latency 를 별도 metric 으로 분리
//
// thresholds:
//   - first_query_after_reload_latency_ms p95 < 500ms — spike 가 있어도 500ms 안에 끝나야
//   - http_req_failed rate < 1%
//   - synonym_apply_failures count == 0 — apply 자체가 실패하면 시나리오 의미 없음

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, randomQuery } from '../lib/config.js';
import { authHeader, operatorHeader } from '../lib/auth.js';

const firstAfterReload = new Trend('first_query_after_reload_latency_ms', true);
const baseline = new Trend('baseline_query_latency_ms', true);
const applyLatency = new Trend('synonym_apply_latency_ms', true);
const applyFail = new Counter('synonym_apply_failures');
const reloadCycles = new Counter('synonym_reload_cycles');

export const options = {
  scenarios: {
    reload_cycle: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5s', target: 3 },
        { duration: '60s', target: 3 },
        { duration: '5s', target: 0 },
      ],
      gracefulRampDown: '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],   // admin endpoint 권한 4xx 가능성 — 5% 까지 허용
    first_query_after_reload_latency_ms: ['p(95)<500'],
    synonym_apply_failures: ['count==0'],
  },
};

function searchOnce() {
  const q = randomQuery();
  const url = `${BASE_URL}/api/v1/search/products`;
  const body = JSON.stringify({ keyword: q, page: 0, size: 20 });
  return http.post(url, body, {
    headers: { 'Content-Type': 'application/json', ...authHeader() },
    tags: { name: 'synonym-hot-reload-search' },
  });
}

function registerOneSynonym(vuId, iter) {
  const url = `${BASE_URL}/api/v1/admin/synonyms`;
  // 같은 의미의 영문 / 한국어 쌍을 무작위로 새로 추가 — terms 가 겹쳐도 도메인이 거부하면
  // 4xx 반환, apply 자체에는 영향 없음.
  const tag = `${vuId}-${iter}-${Date.now()}`;
  const body = JSON.stringify({
    terms: [`k6term-${tag}-a`, `k6term-${tag}-b`],
    direction: 'BIDIRECTIONAL',
  });
  return http.post(url, body, {
    headers: { 'Content-Type': 'application/json', ...authHeader(), ...operatorHeader('k6-synonym') },
    tags: { name: 'synonym-register' },
  });
}

function applySynonyms() {
  const url = `${BASE_URL}/api/v1/admin/synonyms/apply`;
  return http.post(url, null, {
    headers: { ...authHeader(), ...operatorHeader('k6-synonym') },
    tags: { name: 'synonym-apply' },
  });
}

export default function () {
  // 1) baseline 5 회 — warm-up + 평소 분포 측정.
  for (let i = 0; i < 5; i++) {
    const res = searchOnce();
    baseline.add(res.timings.waiting);
    check(res, { 'baseline 200': (r) => r.status === 200 });
    sleep(0.1);
  }

  // 2) 새 동의어 등록 후 apply.
  const reg = registerOneSynonym(__VU, __ITER);
  check(reg, { 'register 2xx': (r) => r.status >= 200 && r.status < 300 });

  const applyStart = Date.now();
  const applyRes = applySynonyms();
  applyLatency.add(Date.now() - applyStart);
  const applyOk = check(applyRes, { 'apply 2xx': (r) => r.status >= 200 && r.status < 300 });
  if (!applyOk) {
    applyFail.add(1);
    sleep(1);
    return;
  }
  reloadCycles.add(1);

  // 3) 적용 직후 첫 검색 — cache invalidation 비용을 그대로 받는다.
  const firstRes = searchOnce();
  firstAfterReload.add(firstRes.timings.waiting);
  check(firstRes, { 'first-after 200': (r) => r.status === 200 });

  // 4) 이어서 4 회 더 호출 — cache warm 으로 복귀하는 모양을 본다 (baseline 에 합산).
  for (let i = 0; i < 4; i++) {
    const res = searchOnce();
    baseline.add(res.timings.waiting);
    sleep(0.1);
  }

  sleep(1);
}
