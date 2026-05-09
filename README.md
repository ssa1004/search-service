# Search Service

한정판 sneaker / commerce 상품 검색 서비스의 백엔드입니다. 키워드 검색, 자동완성, faceted
filter, 사용자 검색 클릭 로그 기반 boost, CDC 인덱싱 파이프라인을 제공합니다.

KREAM, 무신사, 쿠팡, 카카오 등 commerce 검색 플랫폼을 모티브로 삼았습니다.

## 기술 스택

- **Language**: Java 21 (virtual threads)
- **Framework**: Spring Boot 3.4.1
- **Source DB**: PostgreSQL 16, Flyway
- **Search Engine**: Elasticsearch 8.15 (공식 Java Client)
- **Messaging**: Apache Kafka (CDC topic + click log topic)
- **Resilience**: Resilience4j (Circuit Breaker, Retry, Bulkhead — ES 호출 보호)
- **Build / CI**: Gradle 8, GitHub Actions, Docker, Kubernetes

상세 모듈 / 흐름 / 운영 가이드는 작업 진행에 따라 채워집니다.
