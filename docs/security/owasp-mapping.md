# OWASP API Security Top 10 (2023) — search-service 매핑

본 문서는 OWASP API Security Top 10 (2023) 의 각 항목이 search-service 의 어디에 해당하고,
현재 어떻게 다루고 있는지를 정리한다. 검색 도메인 (Lucene / Elasticsearch 질의 + 동의어 사전 +
저장된 검색 + 분석) 의 특성상 **자원 소비 (API4)** 가 가장 큰 표면이라 그 항목에 가장 많은
방어가 들어가 있다.

서비스 자체는 인증 / 인가 게이트웨이를 가지지 않는 *데모 / 포트폴리오용* 백엔드다 — 운영에서는
API Gateway / Service Mesh 가 JWT 검증, 사용자 식별 헤더 (`X-User-Id`) 주입, admin endpoint 보호
(`/api/v1/admin/**`) 를 책임진다고 가정한다. 본 문서는 그 가정 위에서 *서비스 코드가 책임지는*
부분만 다룬다.

## 요약

| ID    | 항목                                  | 표면 | 상태  |
|-------|---------------------------------------|------|-------|
| API1  | Broken Object Level Authorization     | 작음 | 가정 ([§API1](#api1-broken-object-level-authorization)) |
| API2  | Broken Authentication                 | 작음 | 가정 ([§API2](#api2-broken-authentication)) |
| API3  | Broken Object Property Authorization  | 중간 | 적용 ([§API3](#api3-broken-object-property-authorization)) |
| API4  | Unrestricted Resource Consumption     | 큼   | **집중 방어** ([§API4](#api4-unrestricted-resource-consumption)) |
| API5  | Broken Function Level Authorization   | 중간 | 가정 ([§API5](#api5-broken-function-level-authorization)) |
| API6  | Unrestricted Access to Sensitive Flow | 작음 | 가정 ([§API6](#api6-unrestricted-access-to-sensitive-business-flows)) |
| API7  | Server Side Request Forgery           | 작음 | 미해당 ([§API7](#api7-server-side-request-forgery)) |
| API8  | Security Misconfiguration             | 중간 | 일부 ([§API8](#api8-security-misconfiguration)) |
| API9  | Improper Inventory Management         | 작음 | 적용 ([§API9](#api9-improper-inventory-management)) |
| API10 | Unsafe Consumption of APIs            | 작음 | 가정 ([§API10](#api10-unsafe-consumption-of-apis)) |

---

## API1 — Broken Object Level Authorization

**상황 — 다른 사용자의 SavedSearch 접근 가능?**

- 도메인 `SavedSearch.ownerId` 가 소유자 식별. 삭제 use case
  (`DeleteSavedSearchService`) 는 `existing.ownerId().equals(ownerId)` 비교 후 불일치면
  `SavedSearchNotOwnedException` 으로 거부 — BOLA 1차 방어가 도메인 안에 있음.
- 단, REST endpoint 가 아직 노출되지 않아 (`SavedSearch` 는 application + adapter-out 만 존재)
  외부에서 직접 접근할 경로가 없다. 향후 endpoint 를 추가할 때 `ownerId` 는 게이트웨이가 주입한
  사용자 식별 헤더에서 받아야 하며 — *path / body 의 ownerId 를 신뢰하지 말아야 한다*. 본 문서로
  계약 명시.

**커버**: `SavedSearch.ownerId` 비교가 서비스 계층에서 강제됨 (`DeleteSavedSearchService.delete`).

---

## API2 — Broken Authentication

**상황 — JWT 검증.**

- 서비스 코드에 JWT 검증이 없다 (Spring Security 의존 자체가 없음). 운영에서는 API Gateway
  (또는 service mesh 의 mTLS / JWT filter) 가 검증하고, 검증된 user / role 을 헤더로 전달하는
  구조를 전제로 한다. 운영자 식별은 `X-Operator-Id` 헤더 (audit trail 의 `updatedBy`) 로 직접
  받음.
- 만약 게이트웨이 없이 직노출되면 admin endpoint 가 무방비. 따라서 운영 배포 시 Helm chart 또는
  K8s NetworkPolicy 로 `/api/v1/admin/**` 는 내부망에서만 접근 가능해야 함 (네트워크 격리).

**미해결 — 데모 범위**: 서비스 단독으로 JWT 검증을 추가하지 않는다. 도입한다면 운영 도메인 결정
이후 별도 ADR.

---

## API3 — Broken Object Property Authorization

**상황 — 검색 결과의 sensitive field redaction.**

- `SearchResult.Hit` 는 의도적으로 *공개해도 안전한 필드만* (`id`, `name`, `brand`, `category`,
  `priceWon`, `stockQuantity`, `status`, `score`) 노출하도록 record schema 가 설계됨.
  `IndexDocument` 에 있는 내부 메타 (`version`, `clickCount`, `updatedAt`, `releasedAt`,
  `sizes`) 는 Hit 로 매핑되지 않아 응답에 새지 않음.
- ES 측 `_source filter` 도 함께 사용 — `autocomplete` / `related` 는 `includes("name", "id")`,
  `includes("name", "clickCount")` 로 제한된 필드만 가져온다.
- 예외 응답: `GlobalExceptionHandler` 는 모든 5xx 에 대해 내부 메시지를 노출하지 않고
  `INTERNAL_ERROR` 만 반환. JSON 파싱 실패 (`HttpMessageNotReadableException`) 도 Jackson 의
  position / field 정보를 노출하지 않도록 sanitize.

**커버**: response DTO 가 read model 의 화이트리스트로 작용. 도메인이 추가 필드를 가져도 응답
schema 가 자동으로 새지 않는 구조.

---

## API4 — Unrestricted Resource Consumption

**검색 도메인의 가장 큰 표면.** Lucene / ES 질의의 비용은 페이지 깊이 / facet cardinality /
match 분석 비용에 비선형으로 비례하므로 입력 단계 컷이 가장 효과적이다.

| 위협 | 방어 위치 | 정책 |
|------|-----------|------|
| **Deep pagination** — `from + size` 가 ES `index.max_result_window` (default 10,000) 를 넘어 cluster 비용 폭증 | `Page` (domain) | `(number + 1) * size <= 10,000` 강제. 위반 시 `IllegalArgumentException`. `size` 자체도 `<= 100`. |
| **Page size 과다** | `Page` (domain) + `SearchDtos.SearchRequest` (DTO 검증) | size `1..100`. |
| **Filter / facet 폭증** — 한 요청에 수십~수백 개 filter / facet 으로 bool query / aggregation 메모리 폭주 | `SearchDtos.SearchRequest` | `filters <= 20`, `facets <= 10`. |
| **Terms 필터 값 폭증** — `terms: [v1,...,vN]` 으로 N 값에 비례한 inverted-index 조회 비용 | `SearchDtos.FilterDto` | `values <= 100` (Bean Validation `@Size`). |
| **Keyword 길이 폭증** — multi_match 분석 비용은 입력 길이에 비례 | `SearchDtos.SearchRequest` / `AutocompleteCommand` / `SuggestRelatedCommand` | keyword `<= 200`, autocomplete prefix `<= 100`, related keyword `<= 200`. |
| **Facet cardinality** — terms aggregation 의 `size` 가 크면 메모리 폭증 (ADR-0008) | `FacetSpec.Terms` (domain) | `size <= 100`. |
| **Wildcard `*:*` / regex** | 사용 안 함 | 컨트롤러는 도메인 `SearchQuery` 만 받음. `keyword` 는 multi_match 로만 들어가 (term, terms, range, exists 외) regex / wildcard 변환 경로가 없음. 운영자가 임의 ES DSL 을 주입할 수 없는 구조. |
| **Cursor / scroll** | 사용 안 함 | `from + size` 페이지네이션만. scroll / search_after 는 도메인에 아예 없음 — 운영 도입 시 별도 ADR. |
| **ReDoS** | 정적 패턴만 | 사용자 입력을 정규식으로 컴파일하는 경로 없음. `SynonymGroup.FORBIDDEN` 만 컴파일 (constant). |
| **`_delete_by_query` 우회** | endpoint 미존재 | 삭제 경로는 CDC consumer 의 `DELETE` op 한 가지 + admin reindex 의 `dropOld`. 사용자 입력으로 `_delete_by_query` 를 호출하는 경로 없음. |
| **ES 연결 폭주** | `Resilience4j` (ADR-0012) | Retry 3회 + Circuit Breaker (50% failure → 30s OPEN) 가 caller 의 빠른 fail 보장. cascade 차단. |
| **분석 INSERT burst** | `SearchAnalyticsConfig` (ADR-0018) | 비동기 thread pool (core 4 / max 8 / queue 1000), 초과 시 `DiscardOldestPolicy`. 검색 응답을 막지 않음. |
| **SavedSearch quota** | `SavedSearch.MAX_PER_OWNER = 50` (domain) | 한 사용자가 등록 가능한 SavedSearch 가 50개로 cap — 무한 등록 차단. `SaveSearchService` 가 commit 전 count check. |
| **SavedSearch 한 사이클 매치 수** | `EvaluateSavedSearchesService.MAX_MATCHES_PER_SEARCH = 50` | 한 평가에서 한 SavedSearch 가 반환하는 product 수 상한 — 알림 message 폭주 방지. |
| **분석 limit** | `QueryAnalyticsService.MAX_LIMIT = 100` | top queries / zero-result queries 응답 row 수 cap. |
| **DLT replay maxRecords** | `AdminDltController.replay(maxRecords)` | default 100, 호출자가 batch 분할. |

**핵심 커밋**: `Page` 의 `(number + 1) * size <= MAX_WINDOW` 가드, `SearchRequest` 의
`@Size(max=...)`, `AutocompleteCommand.MAX_PREFIX_LENGTH`, `SuggestRelatedCommand.MAX_KEYWORD_LENGTH`.

**부수 — 운영 측 보조**:
- ES 측 `index.max_result_window` 는 default (10,000) 를 유지. 도메인이 같은 값을 강제하므로
  cluster 가 거부하기 전에 400 으로 응답.
- API Gateway / WAF 단의 rate limit 은 본 서비스 외부 책임 (게이트웨이 가정).

---

## API5 — Broken Function Level Authorization

**상황 — 운영자 admin endpoint (synonym / reindex / DLT replay / analytics).**

- 4개 admin controller (`AdminSynonymController`, `AdminIndexController`, `AdminDltController`,
  `AdminAnalyticsController`) 가 모두 `/api/v1/admin/**` prefix. 각 컨트롤러 Javadoc 에 "운영
  보안 게이트 (네트워크 분리 또는 인증 미들웨어) 뒤에 둔다" 명시.
- 운영자 식별은 `X-Operator-Id` 헤더 (synonym 등록 시 `updatedBy` 로 저장 — audit trail).
- 서비스 자체에 RBAC 가 없으므로, **운영 배포 시 K8s NetworkPolicy 또는 Ingress 단에서**
  `/api/v1/admin/**` 를 내부망 / 운영자 VPN 으로만 노출해야 한다 (운영 책임).

**미해결 — 데모 범위**: 서비스 단독 RBAC 미도입.

---

## API6 — Unrestricted Access to Sensitive Business Flows

**상황 — 검색 enumeration / scraping.**

- 서비스 자체는 검색 결과의 무제한 enumeration 을 막지 않는다. 단:
  - Deep pagination 차단 (API4) 으로 단일 쿼리로 10,000 건 이상 끌어가는 것은 차단.
  - 응답 schema 가 *공개 가능 필드만* 가져 (price, name, brand, status 등 카탈로그 정보), 민감
    데이터 (재고 외 운영 메타) 가 포함되지 않음 — 카탈로그 scraping 자체가 비즈니스 위협이라면
    별도 anti-bot / rate limit (WAF / 게이트웨이) 가 책임.
- SavedSearch / 클릭 기록은 사용자 식별을 강제하지 않음 — 익명 검색 호환 (`userId == null` 허용).
  분석에서는 anonymous 로 집계.

**미해결 — 도메인 결정**: 무제한 scraping 의 비즈니스 영향이 크면 WAF / rate limit / CAPTCHA 추가.
서비스 코드 범위는 아님.

---

## API7 — Server Side Request Forgery

**상황 — 외부 URL 호출이 있는가?**

- 서비스 코드에 `RestTemplate` / `WebClient` / `HttpClient` 등 outbound HTTP 호출 경로가 없다.
  의존성도 끌어오지 않음.
- 외부와의 통신:
  - ES (`co.elastic.clients` Java Client) — 외부 입력으로 host 가 결정되지 않음 (설정값 고정).
  - Kafka (`KafkaTemplate`) — 사용자 입력이 broker 주소가 되지 않음.
  - `NotifyChannel.WEBHOOK` 도메인 타입은 존재 — 사용자가 등록한 URL 을 받지만, **현재
    publisher 구현 (`KafkaSavedSearchAlertPublisher`) 은 WEBHOOK type 일 때 즉시
    `UnsupportedOperationException` 으로 거부**. 실제 HTTP 호출 코드가 없어 SSRF 가능성 0.
- DLT replay 도 `originalTopic = property("search.cdc.topic")` 에 보내고, 사용자가 destination
  topic 을 좌우하지 않음.

**커버**: 외부 HTTP 호출 경로가 *없음*. WEBHOOK publisher 가 추가될 때는 별도 SSRF 검증 (allowlist
도메인, 내부망 RFC1918 차단, DNS rebinding 방어) 이 필요 — 그 시점에 ADR.

---

## API8 — Security Misconfiguration

**상황 — ES default 보안 / 운영 기본값.**

- ES — `xpack.security.enabled=false` 가 `infrastructure/docker-compose.yml` 에 설정됨.
  *로컬 통합 환경 전용*이며 `SECURITY.md` 에 "외부 노출 환경에서 그대로 쓰지 마세요" 명시. 운영
  배포 시 Helm chart 의 ES 가 별도 — 본 repo 의 docker-compose 와 분리.
- Postgres / Kafka 도 동일한 dev 비밀번호 / `ALLOW_PLAINTEXT_LISTENER` — 로컬 한정.
- Actuator — `health, info, prometheus, metrics` 만 expose. `env`, `beans`, `mappings`,
  `heapdump`, `threaddump` 등 정보 노출 endpoint 는 미공개. `health.show-details=when-authorized`.
- Errors — `GlobalExceptionHandler` 가 5xx 응답에 내부 메시지 / stacktrace 를 담지 않는다.
  Spring Boot 의 default error attributes (`/error` 의 stacktrace 포함) 가 노출되지 않도록
  `server.error.include-stacktrace=never` 가 Spring Boot 3.x default — 변경하지 않음.
- CORS — 별도 설정 없음. default 는 모든 origin 거부. 운영에서 필요하면 게이트웨이가 책임.
- TLS — 본 서비스는 HTTP 만 listen. TLS termination 은 ingress / service mesh 책임.

**부분 미흡 — 데모 범위**:
- ES TLS / auth 가 dev compose 에서 꺼져 있음 (운영 분리 명시).
- HTTP security header (HSTS, Content-Security-Policy 등) 미설정 — 게이트웨이 가정.

---

## API9 — Improper Inventory Management

**상황 — deprecated endpoint.**

- 모든 endpoint 가 `/api/v1/**` 단일 버전. `v0` / 미공개 / shadow endpoint 없음. 검색 endpoint
  목록:
  - `POST /api/v1/search/products`
  - `GET  /api/v1/search/autocomplete`
  - `GET  /api/v1/search/related`
  - `POST /api/v1/search/searches/{searchId}/clicks`
  - `POST /api/v1/admin/index/reindex`
  - `POST /api/v1/admin/synonyms` (+ list / delete / apply)
  - `POST /api/v1/admin/cdc/dlt/replay` (kafka 활성 시)
  - `GET  /api/v1/admin/analytics/{queries/top, queries/zero-result, latency, ctr}`
- `catalog-info.yaml` (Backstage) 가 service inventory 의 진실원본. README + ADR 디렉토리가
  설계/운영 결정 추적.
- 미사용 endpoint / dead code — 본 sweep 에서 미발견.

**커버**: 단일 버전 + Backstage catalog 등록.

---

## API10 — Unsafe Consumption of APIs

**상황 — 외부 API / 외부 데이터 import 의 신뢰.**

- 동의어 사전은 운영자가 REST API 로 직접 등록 (`AdminSynonymController.POST /api/v1/admin/synonyms`).
  외부 dict (예: GitHub 의 synonym.txt) 를 자동으로 import 하는 경로 없음 — 따라서 외부 API
  consumption 자체가 미해당.
- `SynonymGroup` 등록 시 도메인이 검증:
  - term 개수 `2..50`, 각 term 길이 `<= 100`.
  - `,`, `\`, `=>`, 줄바꿈 (`\r`, `\n`) 포함 거부 — ES synonym rule 의 구분자 / line 단위 파서
    오염 차단 (JPA 의 줄바꿈 직렬화 round-trip 문제도 함께).
  - 대소문자 무시 중복 거부 — `Air Jordan 1` / `air jordan 1` 같은 운영자 실수 차단.
- CDC consumer (`CdcConsumer`) 는 자체 서비스가 보낸 outbox 메시지만 수신 (Kafka topic 격리). 외부
  서비스의 임의 메시지를 처리할 수 없음. JSON 파싱 실패 / 알 수 없는 `op` 는 예외로 DLT 라우팅.

**커버**: 외부 데이터 자동 import 없음. 운영자 직접 입력에 대해 도메인 검증.

---

## 변경 로그

- 초안 작성 (이 commit). OWASP API 2023 매핑 + API4 강화: `Page` deep pagination 가드,
  `AutocompleteCommand` / `SuggestRelatedCommand` 길이 cap, `SearchRequest` filter / facet /
  terms-values size cap, `GlobalExceptionHandler` 의 JSON 파싱 / 파라미터 누락 / 타입 불일치
  안전한 에러 응답.
