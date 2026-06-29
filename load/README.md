# Load test (k6)

search-service 의 5 가지 부하 시나리오. ES + nori + function_score boost(= 글자 매칭 점수에 인기·신상 같은 추가 점수를 곱해 더 좋은 상품을 위로 올리는 방식) + 동의어
런타임 reload(= 검색을 멈추지 않고 운영 중에 동의어 사전을 바꿔 즉시 반영하는 것) 구조에 대해 단순 RPS 가 아닌 *검색 특유의 비용* 을 본다 — query DSL,
facet aggregation, 깊은 page (from+size) 페이지네이션 안정성, 동의어 hot-reload 직후 cache invalidation 비용.

## 디렉토리

```
load/
├── README.md
└── k6/
    ├── lib/
    │   ├── auth.js          # mock JWT / X-Operator-Id 헬퍼
    │   └── config.js        # BASE_URL, 검색어 / 브랜드 / 가격 pool
    └── scenarios/
        ├── search-simple.js          # POST 단순 검색 — 단순 latency
        ├── search-with-filters.js    # POST filter+facet+sort — query DSL 비용
        ├── saved-search-create.js    # POST /saved-searches (REST 미노출 — 서비스만 존재) — write throughput
        ├── cursor-pagination.js      # page/size 깊은 페이지 — pagination latency
        └── synonym-hot-reload.js     # admin apply 직후 spike 측정
```

## 사전 준비

세 가지 방법 중 하나.

### A. brew 로 로컬 설치

```bash
brew install k6
k6 version
```

### B. docker 직접 실행

```bash
docker run --rm -i grafana/k6 run - < load/k6/scenarios/search-simple.js
```

### C. docker-compose 통합 환경

`infrastructure/docker-compose.yml` 또는 `docker-compose.integration.yml` 로 본 app +
PostgreSQL + Elasticsearch + Kafka 를 띄운 뒤 k6 를 외부에서 호출.

```bash
docker compose -f infrastructure/docker-compose.yml up -d
SEARCH_ENGINE=elasticsearch SEARCH_KAFKA_ENABLED=true \
  ELASTICSEARCH_HOST=localhost:9200 KAFKA_BOOTSTRAP=localhost:9092 \
  ./gradlew :search-bootstrap:bootRun
./scripts/demo.sh   # 시드 product 일부 색인
```

메모리 모드 (`SEARCH_ENGINE=memory`) 로도 모든 시나리오는 동작한다. 단 latency 절대값은
ES 가 도는 환경 기준의 thresholds 와 잘 안 맞을 수 있어, 임계 비교는 `--no-thresholds`
또는 `K6_THRESHOLDS_OFF=1` 등으로 풀고 쓰는 것을 권장.

## 시나리오별 실행

### 1) search-simple — 단순 검색 latency

가장 일반적인 검색 경로. filter / facet / sort 가 모두 빈 query 라 ES 비용은 bm25 +
function_score (인기도 + 신상품 decay) 만. boost rule cache 와 nori analyzer 의 base
latency 를 본다.

```bash
k6 run load/k6/scenarios/search-simple.js
```

| metric | 기준 |
|---|---|
| `http_req_duration{name:search-simple}` p95 / p99 | < 100ms / 250ms |
| `http_req_failed` | < 1% |
| `search_simple_query_latency_ms` p95 (보조) | < 100ms |

### 2) search-with-filters — query DSL 비용

`POST /api/v1/search/products` 의 풀 body. filter (terms + range), facet (terms + range),
sort 까지 모두 채운 풀 query 가 ES 의 bool / aggregation tree 를 가장 무겁게 만든다.
단순 검색의 절반 RPS 로 운용해 facet aggregation + filter cache miss 의 *순수* 비용을
격리해 본다.

```bash
k6 run load/k6/scenarios/search-with-filters.js
```

| metric | 기준 |
|---|---|
| `http_req_duration{name:search-with-filters}` p95 / p99 | < 300ms / 600ms |
| `facet_compute_ms` p95 (보조 — server-side waiting) | < 200ms |
| `http_req_failed` | < 1% |

### 3) saved-search-create — write throughput

`POST /api/v1/saved-searches` (REST 미노출 — 서비스는 구현됨). 현재 `SaveSearchService` 와
`SavedSearchRepository` (JPA) 등 application/adapter 계층은 존재하지만 이를 노출하는
컨트롤러가 아직 와이어링되지 않아, 이 시나리오는 read 경로와 달리 INSERT 가 들어가는
쓰기 경로를 *상정한* 모델이다. DB connection pool / 트랜잭션 비용이 두드러지는 지점으로,
VU 를 0 → 50 까지 ramp 해 한정판 release 직전 같은 alert 등록 트래픽을 흉내낸다.

```bash
k6 run load/k6/scenarios/saved-search-create.js
```

| metric | 기준 |
|---|---|
| `http_req_duration{name:saved-search-create}` p95 / p99 | < 150ms / 400ms |
| `http_req_failed` | < 1% |
| `saved_search_created` count | > 0 |

### 4) pagination — page/size invariant

한 검색 결과를 `page` 를 0 부터 증가시키며 빈 페이지가 나올 때까지 따라간다. 실제 검색
응답은 cursor / nextCursor 가 아니라 from+size (`page`/`size`, `totalHits`) 방식이라,
cursor token decode 가 아니라 *page invariant 검증* — 어느 페이지에서도 누락 없이 (missing_page
== 0), 누적 hits 가 totalHits 와 닫혀야 한다. 무한루프 안전망으로 50 페이지 (1000건)
까지만.

```bash
k6 run load/k6/scenarios/cursor-pagination.js
```

> 주의: 아래 `cursor_*` metric 은 scenario script (`cursor-pagination.js`) 가 cursor /
> nextCursor 응답을 *상정한* (계획/미구현) 이름이다. 실제 검색 응답은 from+size
> (`page`/`size`, `totalHits`) 라 cursor token decode 는 일어나지 않으며, page invariant
> (누락 없이 totalHits 와 닫힘) 관점으로 읽는다.

| metric | 의미 / 기준 |
|---|---|
| `cursor_decode_error` count | 0 (invariant — 실제 응답엔 cursor 없음, page 누락 관점) |
| `cursor_missing_page` count | 0 (invariant — 끝까지 따라가도 누락 없음) |
| `cursor_pages_per_query` | 분포 관측 — 평균 N 페이지로 닫히는지 |
| `cursor_last_page_latency_ms` p95 | 깊은 페이지의 server-side waiting |
| `http_req_failed` | < 1% |

### 5) synonym-hot-reload — cache invalidation 효과

운영자가 동의어를 등록하고 `/api/v1/admin/synonyms/apply` 를 호출하면 ES settings
update + alias swap 으로 무중단 적용된다 (ADR-0017). 적용 직후 첫 검색은 새 analyzer
chain 으로 query 가 다시 빌드돼 cache invalidation 비용을 그대로 받는다. baseline 분포와
"apply 직후 첫 query" 분포를 분리 측정.

```bash
k6 run load/k6/scenarios/synonym-hot-reload.js
```

| metric | 기준 |
|---|---|
| `first_query_after_reload_latency_ms` p95 | < 500ms (spike 허용 상한) |
| `baseline_query_latency_ms` p95 (보조) | baseline 분포 비교 — alert 가 아닌 관측 |
| `synonym_apply_latency_ms` | apply 자체 비용 |
| `synonym_apply_failures` count | 0 (invariant) |
| `http_req_failed` | < 5% (admin endpoint 권한 4xx 여유) |

## 한 번에 실행

```bash
./scripts/run-load.sh
```

search-simple → search-with-filters → saved-search-create → cursor-pagination →
synonym-hot-reload 순으로 단계 실행. 각 단계 결과는 `build/k6-reports/` 에 JSON 으로
떨군다.

## 환경변수

| key | 기본 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | HTTP base |
| `K6_TOKEN` | (빈 값) | prod 또는 ingress JWT 게이트일 때만 의미 |
| `K6_QUERIES` | 10개 한/영 혼합 키워드 | round-robin 검색어 CSV |
| `K6_PROMETHEUS_RW_SERVER_URL` | (빈 값) | Prom remote-write target. 비면 disable. |

## 검색 특유 측정 항목

REST 부하 측정에 흔히 보는 `http_req_duration` / `http_req_failed` 외에, 검색 service
특유의 비용을 분리해 보기 위해 다음 metric 들을 시나리오 안에서 직접 정의한다.

| 커스텀 metric | 시나리오 | 의미 |
|---|---|---|
| `search_simple_query_latency_ms` | search-simple | server-side TTFB — 단순 검색의 *순수* query latency. 네트워크 변수를 뺀 비교. |
| `facet_compute_ms` | search-with-filters | 풀 query 의 server-side waiting. simple 과의 차이가 facet 비용. |
| `saved_search_created` | saved-search-create | 성공 INSERT 카운터 — 4xx 비율의 보조 지표. (REST 미노출 — 서비스만 존재, 컨트롤러 미와이어링) |
| `cursor_decode_error` | cursor-pagination | (계획/미구현 명명) 실제 응답엔 cursor 가 없어 항상 0 — page 누락 관점의 invariant. |
| `cursor_missing_page` | cursor-pagination | 끝까지 따라갔는데 누적 hits 가 totalHits 보다 적은 건수. |
| `cursor_pages_per_query` | cursor-pagination | 한 query 가 평균 몇 page (`page`/`size`) 로 닫히는지. |
| `cursor_last_page_latency_ms` | cursor-pagination | 깊은 page (from+size) 의 server-side waiting. |
| `first_query_after_reload_latency_ms` | synonym-hot-reload | apply 직후 첫 query 의 spike. cache invalidation 비용. |
| `baseline_query_latency_ms` | synonym-hot-reload | apply 전 / cache warm 분포. spike 비교의 baseline. |
| `synonym_apply_latency_ms` | synonym-hot-reload | settings update + alias swap 자체 비용. |

## k6 표준 metric 해석

| metric | 의미 |
|---|---|
| `vus` / `vus_max` | 현재 / 최대 VU |
| `iter_duration` | 한 default 함수 실행 시간 — sleep 포함 |
| `http_req_duration` | HTTP 응답 소요 — connect / TLS / waiting 합 |
| `http_req_waiting` | TTFB (server-side latency 의 근사) |
| `http_req_failed` | non-2xx 비율 |
| `data_received` / `data_sent` | byte 카운터 |

### p95 / p99 보는 법

- **p95** 는 변동성 신호 (95 백분위) — 일상 SLO 의 기준.
- **p99** 는 꼬리 신호 — GC, ES query cache miss, R2DBC 풀 고갈 등 드문 이벤트.
- p95 → p99 격차가 크면 운영 환경의 reliability tail 이 두꺼운 것 — Resilience4j circuit
  breaker 의 slow-call threshold 와 ES query cache size 부터 본다.

### 시나리오별 부하 모델

| 시나리오 | executor | 모델 |
|---|---|---|
| search-simple | constant-arrival-rate | 500 req/s, 60s |
| search-with-filters | constant-arrival-rate | 200 req/s, 60s |
| saved-search-create | ramping-vus | 0 → 50 VU, 90s |
| cursor-pagination | ramping-vus | 0 → 10 VU, 80s |
| synonym-hot-reload | ramping-vus | 0 → 3 VU, 70s |

`constant-arrival-rate` 는 throughput 기준 (read), `ramping-vus` 는 connection / 누적
상태가 중요한 (write / cursor / hot-reload) 쪽에 쓴다.

## 결과 plot

각 시나리오를 `--out json=build/k6-reports/<name>.json` 으로 떨궈서 dashboard 에 올릴 수
있다.

## Prometheus remote-write 연동 (commerce-ops 통합 대시보드)

5 시나리오 결과를 `commerce-ops` 의 Prometheus 로 흘려보내 한 Grafana 대시보드에서
client load + server actuator 를 같이 보고 싶을 때:

```bash
# commerce-ops 의 Prom 은 이미 remote-write receiver 가 켜져 있다
docker compose -f /path/to/commerce-ops/infra/docker-compose.yml up -d prometheus grafana

export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
./scripts/run-load.sh
```

`run-load.sh` 가 각 시나리오에 `service=search-service` / `scenario=<name>` tag 를 자동
부여한다. Grafana → **Portfolio Load (k6 + actuator)** 대시보드 (uid `portfolio-load`)
에서 service 변수를 `search-service` 로 선택. 16번 패널 "search cache hit ratio" 가
시나리오 별로 분리되어 보인다. 필요 k6 버전 **0.42+** (experimental-prometheus-rw output).

더 큰 부하는 k6 cloud / k6 distributed mode 가 필요 — 본 시나리오는 single-node 기준
이라 VU 100 ~ 500 선에서 운용한다.
