#!/usr/bin/env bash
# k6 부하 시나리오 5종 일괄 실행.
#
# 단계:
#   1) search-service healthcheck (없으면 bootRun / compose 안내)
#   2) k6 실행 경로 결정 — 우선 로컬 k6, 없으면 docker run
#   3) search-simple → search-with-filters → saved-search-create →
#      cursor-pagination → synonym-hot-reload
#   4) 각 결과는 build/k6-reports/{scenario}.json 에 떨군다
#
# 환경 변수:
#   BASE_URL                       — 시나리오의 endpoint base. 기본 http://localhost:8080
#   K6_TOKEN                       — JWT on 일 때만 의미. dev / mock 경로면 빈 값
#   K6_QUERIES                     — 검색어 pool (CSV). 비어 있으면 config.js default.
#   K6_PROMETHEUS_RW_SERVER_URL    — remote-write target (예 http://localhost:9090/api/v1/write).
#                                    비어 있으면 console 만 (default disabled).

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
SCENARIO_DIR="${ROOT_DIR}/load/k6/scenarios"
REPORT_DIR="${ROOT_DIR}/build/k6-reports"
mkdir -p "$REPORT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8080}"
K6_TOKEN="${K6_TOKEN:-}"
K6_QUERIES="${K6_QUERIES:-}"

# k6 → Prometheus remote-write (optional). commerce-ops Prometheus 가 떠 있을 때
# `K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write` export.
K6_PROMETHEUS_RW_SERVER_URL="${K6_PROMETHEUS_RW_SERVER_URL:-}"
K6_PROMETHEUS_RW_TREND_STATS="${K6_PROMETHEUS_RW_TREND_STATS:-p(95),p(99),min,max,avg}"
K6_PROMETHEUS_RW_PUSH_INTERVAL="${K6_PROMETHEUS_RW_PUSH_INTERVAL:-5s}"
SERVICE_TAG="search-service"

echo "==> base url: $BASE_URL"
if [[ -n "$K6_PROMETHEUS_RW_SERVER_URL" ]]; then
    echo "==> k6 → Prometheus RW: $K6_PROMETHEUS_RW_SERVER_URL (service=$SERVICE_TAG)"
fi

# 1) healthcheck
echo
echo "==> health 확인 ($BASE_URL/actuator/health)"
if ! curl -sf "$BASE_URL/actuator/health" >/dev/null 2>&1; then
    cat <<EOF
ERROR: $BASE_URL 가 응답하지 않습니다.

먼저 본 앱을 띄우세요:

  1) 단독 bootRun (가벼움 — memory mode):
       SEARCH_ENGINE=memory ./gradlew :search-bootstrap:bootRun

  2) 통합 compose (ES + Kafka + PG):
       docker compose -f infrastructure/docker-compose.yml up -d
       SEARCH_ENGINE=elasticsearch SEARCH_KAFKA_ENABLED=true \\
         ELASTICSEARCH_HOST=localhost:9200 KAFKA_BOOTSTRAP=localhost:9092 \\
         ./gradlew :search-bootstrap:bootRun

또는 BASE_URL 를 staging 등으로 덮어쓰세요 (예: BASE_URL=http://staging:8080).
EOF
    exit 1
fi
echo "    UP"

# 2) k6 실행 경로
if command -v k6 >/dev/null 2>&1; then
    K6_EXEC=("k6")
    echo "==> 로컬 k6 사용 ($(k6 version | head -1))"
elif command -v docker >/dev/null 2>&1; then
    if [[ "$BASE_URL" == *"localhost"* || "$BASE_URL" == *"127.0.0.1"* ]]; then
        BASE_URL_DOCKER="${BASE_URL//localhost/host.docker.internal}"
        BASE_URL_DOCKER="${BASE_URL_DOCKER//127.0.0.1/host.docker.internal}"
    else
        BASE_URL_DOCKER="$BASE_URL"
    fi
    K6_RW_URL_DOCKER="${K6_PROMETHEUS_RW_SERVER_URL//localhost/host.docker.internal}"
    K6_RW_URL_DOCKER="${K6_RW_URL_DOCKER//127.0.0.1/host.docker.internal}"
    K6_EXEC=(docker run --rm -i \
        -v "${ROOT_DIR}/load/k6:/scripts:ro" \
        -e "BASE_URL=${BASE_URL_DOCKER}" \
        -e "K6_TOKEN=${K6_TOKEN}" \
        -e "K6_QUERIES=${K6_QUERIES}" \
        -e "K6_PROMETHEUS_RW_SERVER_URL=${K6_RW_URL_DOCKER}" \
        -e "K6_PROMETHEUS_RW_TREND_STATS=${K6_PROMETHEUS_RW_TREND_STATS}" \
        -e "K6_PROMETHEUS_RW_PUSH_INTERVAL=${K6_PROMETHEUS_RW_PUSH_INTERVAL}" \
        grafana/k6:0.50.0)
    SCRIPT_PREFIX="/scripts/scenarios"
    echo "==> docker run grafana/k6 사용"
else
    echo "ERROR: k6 도 docker 도 없습니다. brew install k6 또는 docker 설치 후 다시 시도하세요." >&2
    exit 1
fi

# 3) 시나리오 실행 — 한 단계 실패해도 다음 단계는 진행
run_scenario() {
    local name="$1"
    local file="$2"

    echo
    echo "==> [$name] start ($(date +%H:%M:%S))"
    local out="${REPORT_DIR}/${name}.json"
    local rc=0

    local rw_opts=()
    if [[ -n "$K6_PROMETHEUS_RW_SERVER_URL" ]]; then
        rw_opts=(-o "experimental-prometheus-rw" \
                 --tag "service=${SERVICE_TAG}" \
                 --tag "scenario=${name}")
    fi

    if [[ "${K6_EXEC[0]}" == "k6" ]]; then
        export BASE_URL K6_TOKEN K6_QUERIES \
               K6_PROMETHEUS_RW_SERVER_URL K6_PROMETHEUS_RW_TREND_STATS K6_PROMETHEUS_RW_PUSH_INTERVAL
        set +e
        "${K6_EXEC[@]}" run "${rw_opts[@]}" --summary-export="$out" "$file"
        rc=$?
        set -e
    else
        local docker_file="${SCRIPT_PREFIX}/$(basename "$file")"
        local docker_out="/scripts/${name}.summary.json"
        set +e
        "${K6_EXEC[@]}" run "${rw_opts[@]}" --summary-export="$docker_out" "$docker_file"
        rc=$?
        set -e
        if [[ -f "${ROOT_DIR}/load/k6/${name}.summary.json" ]]; then
            mv "${ROOT_DIR}/load/k6/${name}.summary.json" "$out"
        fi
    fi

    if [[ $rc -eq 0 ]]; then
        echo "==> [$name] PASSED (report: $out)"
    else
        echo "==> [$name] FAILED rc=$rc (report: $out)"
    fi
}

# 실행 순서:
#   - search-simple → search-with-filters (단순 read → 풀 DSL read)
#   - saved-search-create (write throughput)
#   - cursor-pagination (cursor decode invariant)
#   - synonym-hot-reload 마지막에 — admin apply 가 캐시 무효화를 일으키므로 다른 시나리오의
#     baseline 측정에 spike 가 섞이지 않도록 분리.
run_scenario "search-simple"        "${SCENARIO_DIR}/search-simple.js"
run_scenario "search-with-filters"  "${SCENARIO_DIR}/search-with-filters.js"
run_scenario "saved-search-create"  "${SCENARIO_DIR}/saved-search-create.js"
run_scenario "cursor-pagination"    "${SCENARIO_DIR}/cursor-pagination.js"
run_scenario "synonym-hot-reload"   "${SCENARIO_DIR}/synonym-hot-reload.js"

echo
echo "==> 모든 시나리오 종료. 리포트: $REPORT_DIR"
ls -lah "$REPORT_DIR" 2>/dev/null || true
