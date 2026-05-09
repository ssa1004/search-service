// Inbound adapter — REST controllers + Kafka consumers (CDC).
// Application 의 UseCase 인터페이스만 호출.
plugins {
    `java-library`
}

dependencies {
    implementation(project(":search-application"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kafka consumer — CDC 메시지 수신.
    implementation("org.springframework.kafka:spring-kafka")

    // OpenAPI.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Tracing.
    implementation("io.micrometer:micrometer-tracing")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}
