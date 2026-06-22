# OpenAPI spec

`search-service` 의 REST API 를 OpenAPI 3 spec 으로 build-time export 한다.

## 무엇이 들어가나

- `search-service.yaml` — 빌드 시 생성되는 OpenAPI 3 문서. 외부 참조 / SDK codegen 의 단일 진실값.
  - 검색 (`/api/v1/search` — products / autocomplete / related, 클릭 로그)
  - 인덱스 운영 (`/api/v1/admin/index/reindex`)
  - synonym 사전 / 분석 (`/api/v1/admin/synonyms`, `/api/v1/admin/analytics`)

> CDC DLT replay endpoint (`/api/v1/admin/cdc/dlt/replay`) 는 `search.kafka.enabled=true`
> 일 때만 등록되는 `@ConditionalOnProperty` 컨트롤러라, Kafka 가 꺼진 메모리 모드 spec 에는
> 포함되지 않는다. 메모리 모드는 검색 엔진과 무관한 REST 표면만 노출한다.

> 이 디렉토리의 `*.yaml` 은 CI 에서 생성·갱신된다. 로컬에서 수기로 편집하지 않는다.

## 생성 방법

spec 은 앱을 **메모리 모드**(`SEARCH_ENGINE=memory`)로 띄운 뒤 `/v3/api-docs.yaml` 을 받아
`docs/openapi/search-service.yaml` 로 저장한다. 메모리 모드는 datasource 가 H2(in-memory),
Kafka 비활성, 검색은 `InMemorySearchEngineAdapter` 라 Docker / Postgres / Kafka /
Elasticsearch 없이도 spec 을 생성할 수 있다. REST 컨트롤러 매핑은 검색 엔진과 무관하게
동일하게 노출되므로 생성된 spec 은 운영 모드와 동일하다 (Kafka-gated DLT endpoint 제외 — 위 참고).

권장 방식 — 앱을 띄우고 spec 을 받아 저장:

```bash
SEARCH_ENGINE=memory ./gradlew :search-bootstrap:bootRun --args='--server.port=8080' &
# health 가 UP 될 때까지 대기한 뒤:
curl -s http://localhost:8080/v3/api-docs.yaml -o docs/openapi/search-service.yaml
```

`org.springdoc.openapi-gradle-plugin` 의 `generateOpenApiDocs` 태스크도 동일 산출물을
만든다 — 단, 이 플러그인의 forked JVM 은 Gradle toolchain(JDK 21) 이 아니라 호스트의
default `java` 로 앱을 띄우므로, **호스트 JDK 가 21 이상일 때만** 동작한다 (JDK 17 호스트에서는
`UnsupportedClassVersionError`). CI 는 JDK 21 이므로 양쪽 다 가능하다.

`OpenApiConfig` 가 spec 의 `info`(title/version) 와 `server` 를 고정(`/` 상대경로)하므로
부팅 포트와 무관하게 **결정적(deterministic)** 인 yaml 이 나온다 — drift gate 가 흔들리지 않는다.

## 보는 법

- Swagger UI — 앱 실행 후 `http://localhost:8080/swagger-ui.html`
- Redoc — `npx @redocly/cli preview-docs docs/openapi/search-service.yaml`
- 통합 뷰어 — profile repo `ssa1004/ssa1004` 의 `docs/api/index.html` (11 service spec 드롭다운)
