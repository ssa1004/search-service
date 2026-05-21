# OpenAPI spec

`search-service` 의 REST API 를 OpenAPI 3 spec 으로 build-time export 한다.

## 무엇이 들어가나

- `search-service.yaml` — 빌드 시 생성되는 OpenAPI 3 문서. 외부 참조 / SDK codegen 의 단일 진실값.
  - 검색 (`/api/v1/search` — cursor pagination, 다국어 analyzer)
  - 인덱스 운영 (`/api/v1/admin/index`)
  - synonym 사전 / 분석 (`/api/v1/admin/synonyms`, `/api/v1/admin/analytics`)
  - CDC DLT 운영 (`/api/v1/admin/dlt`)

> 이 디렉토리의 `*.yaml` 은 CI 에서 생성·갱신된다. 로컬에서 수기로 편집하지 않는다.

## 생성 방법

`org.springdoc.openapi-gradle-plugin` 을 `search-bootstrap` 모듈에 적용했다.
`generateOpenApiDocs` 태스크가 앱을 부팅한 뒤 `/v3/api-docs.yaml` 을 받아
`docs/openapi/search-service.yaml` 로 저장한다.

```bash
./gradlew :search-bootstrap:generateOpenApiDocs
```

앱 부팅에 Postgres / Kafka / Elasticsearch 가 필요하므로, 의존 인프라를 먼저 띄워야 한다.
CI 에서는 service container 를 띄운 잡에서 위 태스크를 실행해 산출된 yaml 을
commit 하거나 아티팩트로 업로드한다.

## 보는 법

- Swagger UI — 앱 실행 후 `http://localhost:8080/swagger-ui.html`
- Redoc — `npx @redocly/cli preview-docs docs/openapi/search-service.yaml`
- 통합 뷰어 — profile repo `ssa1004/ssa1004` 의 `docs/api/index.html` (11 service spec 드롭다운)
