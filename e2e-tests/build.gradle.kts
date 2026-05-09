// e2e — Postgres + Kafka + Elasticsearch 통합 시나리오 (Testcontainers).
plugins {
    java
    id("io.spring.dependency-management")
}

dependencies {
    testImplementation(project(":search-bootstrap"))
    // bootstrap 이 implementation 으로 가리고 있어 e2e 에서 직접 import 하려면 명시 필요.
    testImplementation(project(":search-domain"))
    testImplementation(project(":search-application"))
    testImplementation(project(":search-adapter-out"))
    testImplementation(project(":search-adapter-in"))
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.awaitility:awaitility")
    testImplementation("co.elastic.clients:elasticsearch-java:8.15.3")
}
