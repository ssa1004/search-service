#!/usr/bin/env bash
# 메모리 모드 데모 — bootRun 띄운 뒤 실행. 검색 / 자동완성 / 관련 키워드 / 클릭 흐름 한 번.
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
echo "── 5) 운영 reindex (alias swap) ──"
curl -sS -X POST "$BASE/api/v1/admin/index/reindex" \
  -H 'Content-Type: application/json' \
  -d '{"suffix":"v202605","dropOld":false}' | jq
