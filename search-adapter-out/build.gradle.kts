// Outbound adapter — Elasticsearch 클라이언트 + Postgres source + Kafka producer/consumer + CDC indexer worker.
plugins {
    `java-library`
}

dependencies {
    implementation(project(":search-application"))

    // Persistence — source DB (Postgres) + outbox.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Elasticsearch — 공식 Java Client (8.x). Spring Data Elasticsearch 대신 low-level Java Client 직접
    // 사용 — function_score / aggregation 같은 복잡 query 의 DSL 표현력이 더 높고 mapping 도 그대로
    // JSON 으로 정의 가능. (ADR-0002 참고)
    implementation("co.elastic.clients:elasticsearch-java:8.15.3")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("jakarta.json:jakarta.json-api:2.1.3")
    runtimeOnly("org.eclipse.parsson:parsson:1.1.6")

    // Resilience4j — ES 호출 보호 (CB + Retry + Bulkhead).
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    // ShedLock — 멀티 인스턴스에서 @Scheduled 중복 실행 방지 (outbox retention 등).
    // OutboxRetentionJob 이 @SchedulerLock 어노테이션을 사용하므로 api 로 노출.
    api("net.javacrumbs.shedlock:shedlock-spring:5.16.0")

    // Messaging — Kafka producer / consumer.
    implementation("org.springframework.kafka:spring-kafka")

    // Tracing — Micrometer.
    implementation("io.micrometer:micrometer-tracing")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}
