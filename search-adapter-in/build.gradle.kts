// Inbound adapter — REST controllers + Kafka consumers (CDC).
// Application 의 UseCase 인터페이스만 호출.
plugins {
    `java-library`
}

dependencies {
    implementation(project(":search-application"))
    // CDC consumer 가 outbox relay 가 정의한 message envelope (CdcEventPayload + ProductDtoMapper) 를
    // 공유하기 위해 adapter-out 을 의존. inbound 가 outbound 의 wire format 에 결합되는 것은 의도된
    // 설계 — Kafka topic schema 는 한 곳에만 두는 게 단일 진실값.
    implementation(project(":search-adapter-out"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kafka consumer — CDC 메시지 수신.
    implementation("org.springframework.kafka:spring-kafka")

    // OpenAPI.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Tracing + metrics — CdcConsumer / CdcDltConsumer 가 cdc.consume / cdc.dlt 카운터 발행.
    implementation("io.micrometer:micrometer-tracing")
    implementation("io.micrometer:micrometer-core")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}
