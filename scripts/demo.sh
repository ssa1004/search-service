#!/usr/bin/env bash
# 메모리 모드 데모 — bootRun 띄운 뒤 실행.
#
# 흐름:
#   1) 키워드 검색 (filter + facet + boost)
#   2) 자동완성 (edge_ngram prefix)
#   3) 관련 검색어 (fuzzy — zero-result 회복)
#   4) 클릭 시그널 (boost 학습 입력 — ADR-0006)
#   5) 운영자 동의어 등록 + 적용 (ADR-0017)
#   6) 운영 reindex — alias swap (ADR-0005)
#   7) 검색 분석 — top / zero-result / latency / CTR (ADR-0018)
#
# 사전 조건:
#   SEARCH_ENGINE=memory ./gradlew :search-bootstrap:bootRun
#
# memory 모드 한정으로 동작하지 않는 endpoint:
#   - DLT replay (search.kafka.enabled=true 일 때만 빈 등록 — ADR-0013)
#   - 동의어 ES apply (memory 모드는 no-op 으로 응답)
set -euo pipefail

BASE=${BASE:-http://localhost:8080}

echo "── 1) 키워드 검색 ──"
curl -sS -X POST "$BASE/api/v1/search/products" \
  -H 'Content-Type: application/json' \
  -d '{
    "keyword": "Air",
    "filters": [],
    "facets": [{"name":"by-brand","field":"brand","type":"terms","size":10}],
    "page": 0,
    "size": 10
  }' | jq

echo
echo "── 2) 자동완성 ──"
curl -sS "$BASE/api/v1/search/autocomplete?q=Air&limit=5" | jq

echo
echo "── 3) 관련 검색어 (fuzzy) ──"
curl -sS "$BASE/api/v1/search/related?q=Nikr&limit=5" | jq

echo
echo "── 4) 클릭 기록 ──"
curl -sS -X POST "$BASE/api/v1/search/searches/demo-1/clicks" \
  -H 'Content-Type: application/json' \
  -d '{
    "productId": "p-1",
    "userId": "u-1",
    "keyword": "Air",
    "rank": 1
  }' -w '\n클릭 응답 status=%{http_code}\n'

echo
echo "── 5) 운영자 동의어 등록 + 적용 ──"
echo "  · 5-1) AJ1 ↔ Air Jordan 1 등록 (양방향)"
curl -sS -X POST "$BASE/api/v1/admin/synonyms" \
  -H 'Content-Type: application/json' \
  -H 'X-Operator-Id: demo-operator' \
  -d '{
    "terms": ["AJ1", "Air Jordan 1"],
    "direction": "BIDIRECTIONAL"
  }' | jq

echo "  · 5-2) 등록 그룹 조회"
curl -sS "$BASE/api/v1/admin/synonyms" | jq '.groups | length as $n | "registered groups = \($n)"'

echo "  · 5-3) ES 인덱스에 적용 (memory 모드는 no-op)"
curl -sS -X POST "$BASE/api/v1/admin/synonyms/apply" | jq

echo
echo "── 6) 운영 reindex (alias swap) ──"
curl -sS -X POST "$BASE/api/v1/admin/index/reindex" \
  -H 'Content-Type: application/json' \
  -d '{"suffix":"v202605","dropOld":false}' | jq

echo
echo "── 7) 검색 분석 (최근 1시간) ──"
FROM=$(date -u -v-1H '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -d '1 hour ago' '+%Y-%m-%dT%H:%M:%SZ')
TO=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

echo "  · 7-1) 인기 검색어 top"
curl -sS "$BASE/api/v1/admin/analytics/queries/top?from=$FROM&to=$TO&limit=5" | jq

echo "  · 7-2) 0건 검색어 (운영자 동의어 등록 후보)"
curl -sS "$BASE/api/v1/admin/analytics/queries/zero-result?from=$FROM&to=$TO&limit=5" | jq

echo "  · 7-3) 응답 latency p50 / p95 / p99"
curl -sS "$BASE/api/v1/admin/analytics/latency?from=$FROM&to=$TO" | jq

echo "  · 7-4) Click-through rate"
curl -sS "$BASE/api/v1/admin/analytics/ctr?from=$FROM&to=$TO" | jq
