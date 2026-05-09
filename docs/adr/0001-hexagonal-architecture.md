# ADR-0001: 헥사고날 아키텍처 + 모듈 분리

## 상태
적용

## 배경
검색 서비스는 외부 시스템 의존이 많다 — source DB (Postgres), 검색 엔진 (Elasticsearch),
메시지 브로커 (Kafka), 운영 monitoring 까지. 도메인 코드가 이런 인프라 라이브러리에 직접
의존하면 검색 엔진 교체 (ES → OpenSearch), 테스트 격리 (메모리 모드), 운영 변경
(reindex 정책) 의 비용이 빠르게 커진다.

## 결정
**헥사고날 아키텍처 (도메인을 중심에 두고 외부와의 통신은 port → adapter 로 분리하는
구조) 로 6모듈 분리.**

```
search-domain          ← 순수 도메인 (Spring 의존 0, JPA 의존 0)
search-application     ← 유스케이스 + port 인터페이스
search-adapter-in      ← REST + Kafka 컨슈머
search-adapter-out     ← ES 클라이언트 + JPA + outbox + CDC relay
search-bootstrap       ← Spring Boot 진입점 + Config + Flyway
e2e-tests              ← Testcontainers 통합 시나리오
```

의존 방향은 단방향이다.
- `domain` 은 어떤 모듈도 의존하지 않는다.
- `application` 은 `domain` 만 의존한다.
- `adapter-in/out` 은 `application` 의 port 인터페이스만 본다 (도메인 모델은 통과 타입).
- `bootstrap` 만 모든 모듈을 알고 빈을 조립한다.

## 장단점
- 도메인이 ES SDK / Kafka SDK 를 모르므로 검색 엔진 / 브로커 교체가 adapter 추가 / 교체로
  국한된다. `InMemorySearchEngineAdapter` 가 같은 port 를 구현해 ES 없이도 동일 흐름이
  돈다 (단위 테스트 / dev).
- use case 의 단위 테스트가 mock 만으로 가능하다 — Spring 컨텍스트 부팅 비용 없음.
- 모듈 경계가 빌드 단계에서 강제되므로 `domain → adapter` 같은 역방향 import 가 컴파일
  에러로 차단된다.
- 모듈 분리 비용이 있다 — DTO / 매퍼가 layer 마다 늘어난다. 5년차 cold-read 기준 30초
  안에 흐름을 잡으려면 모듈 경계를 명확히 알고 있어야 한다.

## 다시 검토할 시점
- 도메인이 단순해져 use case 가 거의 단순 CRUD 로 수렴할 때 — 그때는 단일 모듈 + 패키지
  분리가 더 빠르게 손에 잡힌다.
- 검색 / 인덱싱 워크플로우가 분리되어 별도 서비스로 떨어져 나갈 때 — 그때는 모듈이 아니라
  서비스 단위로 split.
