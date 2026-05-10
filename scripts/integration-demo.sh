#!/usr/bin/env bash
# Portfolio set 통합 시연 스크립트.
#
# 흐름:
#   1) search-service / auth-stub 헬스 대기
#   2) auth-stub 의 JWK Set 조회 — auth-service 의 /.well-known/jwks.json 모사 응답 확인
#   3) mock JWT 발급 (header.payload.signature 의 평문 base64url — 검증 통과 보장 X.
#      JWT 가 어떤 claim 을 가지는지 시연 + Authorization 헤더 흐름 확인이 목적)
#   4) sample product 색인 — CDC topic product.changes 에 INSERT 메시지 produce →
#      CdcConsumer 가 ES indexing
#   5) 검색 호출 → 색인된 product hit 확인
#   6) SavedSearchAlert 메시지를 search.alert.fired topic 에 produce →
#      notification-hub-stub 가 받는지 docker logs 로 확인
#
# 외부 의존 없음. 모두 docker network 안에서 닫힘.
#
# 실행:
#   docker compose -f infrastructure/docker-compose.integration.yml up -d --build
#   ./scripts/integration-demo.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$REPO_ROOT/infrastructure/docker-compose.integration.yml}"
COMPOSE=(docker compose -f "$COMPOSE_FILE")

SEARCH_BASE="${SEARCH_BASE:-http://localhost:8088}"
AUTH_BASE="${AUTH_BASE:-http://localhost:8085}"
WAIT_SECONDS="${WAIT_SECONDS:-180}"

log()  { printf '\n[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
step() { printf '\n=== %s ===\n' "$*"; }
fail() { printf '\n[FAIL] %s\n' "$*" >&2; exit 1; }

require() {
    command -v "$1" >/dev/null 2>&1 || fail "필수 명령 없음: $1"
}
require curl
require jq
require docker
require base64

wait_for() {
    local name="$1" url="$2" deadline=$(( $(date +%s) + WAIT_SECONDS ))
    log "[wait] $name 헬스 대기 ($url)"
    while (( $(date +%s) < deadline )); do
        if curl -sf "$url" >/dev/null 2>&1; then
            log "[ok]   $name 응답 OK"
            return 0
        fi
        sleep 2
    done
    fail "$name 가 $WAIT_SECONDS 초 안에 응답하지 않음"
}

# base64url encode (no padding, +/ → -_).
b64url() {
    base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n'
}

# ---------- 1. 헬스 대기 ----------
step "1) 컨테이너 헬스 대기"
wait_for "auth-stub"      "$AUTH_BASE/healthz"
wait_for "search-service" "$SEARCH_BASE/actuator/health/readiness"

# ---------- 2. JWK Set 조회 ----------
step "2) auth-stub JWK Set 조회 (/.well-known/jwks.json)"
JWKS=$(curl -sf "$AUTH_BASE/.well-known/jwks.json") || fail "JWK Set 조회 실패"
echo "$JWKS" | jq '.keys[0] | {kty, use, kid, alg}'
KID=$(echo "$JWKS" | jq -r '.keys[0].kid')
log "[ok]   kid=$KID — 본 service 가 jwk-set-uri 로 매핑하면 이 키로 서명 검증"

# ---------- 3. mock JWT 발급 ----------
step "3) mock JWT 발급 (header.payload.signature)"
NOW=$(date +%s)
EXP=$((NOW + 3600))
HEADER=$(printf '{"alg":"RS256","typ":"JWT","kid":"%s"}' "$KID" | b64url)
PAYLOAD=$(printf '{"iss":"http://auth-stub:8080","sub":"demo-user","aud":"search-service","exp":%d,"iat":%d,"scope":"search.read"}' \
    "$EXP" "$NOW" | b64url)
SIG=$(printf 'stub-signature' | b64url)
JWT="$HEADER.$PAYLOAD.$SIG"
log "[ok]   JWT 발급 — exp=$(date -r "$EXP" '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -d "@$EXP" -u '+%Y-%m-%dT%H:%M:%SZ')"
log "       앞 32자: ${JWT:0:32}..."
log "       (현 단계 REST 컨트롤러는 JWT 를 검증하지 않음 — Authorization 헤더 통과 흐름만 시연)"

# ---------- 4-pre. alias 부트스트랩 ----------
step "4-pre) /api/v1/admin/index/reindex — alias products 가 없으면 첫 색인 직전 부트스트랩"
SUFFIX="v$(date -u '+%Y%m%d%H%M%S')"
REINDEX_RES=$(curl -s -o /tmp/reindex.body -w '%{http_code}' \
    -X POST "$SEARCH_BASE/api/v1/admin/index/reindex" \
    -H 'Content-Type: application/json' \
    -d "{\"suffix\":\"$SUFFIX\",\"dropOld\":false}")
[[ "$REINDEX_RES" == "200" ]] || fail "reindex 실패 status=$REINDEX_RES body=$(cat /tmp/reindex.body)"
log "[ok]   reindex 완료 — alias products → products-$SUFFIX"
jq -c '.' /tmp/reindex.body

# ---------- 4. CDC 메시지 → 색인 ----------
step "4) sample product 색인 — CDC topic product.changes 에 INSERT 메시지 produce"
PRODUCT_ID="p-demo-$(date +%s)"
NOW_ISO=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
PRODUCT_PAYLOAD=$(jq -nc \
    --arg id "$PRODUCT_ID" \
    --arg now "$NOW_ISO" \
    '{
        id: $id,
        name: "Air Jordan 1 Demo",
        brand: "Nike",
        category: "SNEAKERS",
        sizes: ["260","270"],
        priceWon: 199000,
        stockQuantity: 5,
        status: "AVAILABLE",
        version: 1,
        releasedAt: $now,
        updatedAt: $now
    }')
CDC_PAYLOAD=$(jq -nc \
    --arg id "$PRODUCT_ID" \
    --arg now "$NOW_ISO" \
    --arg payload "$PRODUCT_PAYLOAD" \
    '{
        op: "INSERT",
        productId: $id,
        version: 1,
        payload: $payload,
        occurredAt: $now
    }')

# domain-producer 컨테이너에 메시지 한 줄 publish — kafka-console-producer.sh 사용.
log "produce CDC INSERT productId=$PRODUCT_ID"
echo "$CDC_PAYLOAD" | "${COMPOSE[@]}" exec -T domain-producer \
    /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server kafka:9092 \
        --topic product.changes \
        --property "parse.key=false"

log "[ok]   produce 완료 — CdcConsumer 가 ES 에 색인 (5초 안 도달 기대)"
sleep 7

# ---------- 5. 검색 호출 ----------
step "5) /api/v1/search/products 호출 — 색인된 product hit 기대"
SEARCH_RES=$(curl -s -o /tmp/search.body -w '%{http_code}' \
    -X POST "$SEARCH_BASE/api/v1/search/products" \
    -H "Authorization: Bearer $JWT" \
    -H 'Content-Type: application/json' \
    -d '{
        "keyword": "Air Jordan",
        "filters": [],
        "facets": [],
        "page": 0,
        "size": 10
    }')
[[ "$SEARCH_RES" == "200" ]] || fail "검색 실패 status=$SEARCH_RES body=$(cat /tmp/search.body)"
HITS=$(jq -r '.totalHits // .total // (.results | length)' /tmp/search.body)
log "[ok]   검색 200 — totalHits=$HITS"
jq '.' /tmp/search.body | head -40

# ---------- 6. SavedSearchAlert publish ----------
step "6) SavedSearchAlert 메시지를 search.alert.fired 에 produce — notification-hub-stub 가 받는지 확인"
ALERT_PAYLOAD=$(jq -nc \
    --arg id "$PRODUCT_ID" \
    --arg now "$NOW_ISO" \
    '{
        savedSearchId: "ss-demo-1",
        ownerId: "demo-user",
        label: "Air Jordan 알림",
        matchedProductIds: [$id],
        totalNewMatches: 1,
        firedAt: $now
    }')

log "produce SavedSearchAlert savedSearchId=ss-demo-1"
echo "ss-demo-1:$ALERT_PAYLOAD" | "${COMPOSE[@]}" exec -T domain-producer \
    /opt/kafka/bin/kafka-console-producer.sh \
        --bootstrap-server kafka:9092 \
        --topic search.alert.fired \
        --property "parse.key=true" \
        --property "key.separator=:"

log "[ok]   produce 완료 — notification-hub-stub 의 console-consumer 가 from-beginning 으로 tail 중"
sleep 3

step "7) notification-hub-stub 로그에서 알림 메시지 확인"
"${COMPOSE[@]}" logs --tail 20 notification-hub-stub | grep -F "ss-demo-1" \
    && log "[ok]   notification-hub-stub 가 ss-demo-1 메시지 수신" \
    || fail "notification-hub-stub 가 ss-demo-1 메시지를 받지 못함"

step "DONE — 통합 시연 완료"
log "정리: docker compose -f $COMPOSE_FILE down -v"
