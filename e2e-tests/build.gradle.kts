// e2e — Postgres + Kafka + Elasticsearch 통합 시나리오 (Testcontainers).
//
// Kotlin 으로 마이그레이션. e2e 테스트 클래스는 mockito-kotlin / kotlin DSL 을 사용하지 않지만 도메인
// 코드가 Kotlin 으로 전환되어 함께 정렬.
plugins {
    java
    kotlin("jvm")
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
    testImplementation("co.elastic.clients:elasticsearch-java:9.4.1")

    // NoriAnalyzerIT 가 raw REST 호출을 Jackson 으로 파싱.
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
