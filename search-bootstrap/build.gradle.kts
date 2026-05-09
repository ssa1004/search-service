// Spring Boot 진입점. main + 통합 config + Flyway + ES mapping init.
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":search-domain"))
    implementation(project(":search-application"))
    implementation(project(":search-adapter-in"))
    implementation(project(":search-adapter-out"))

    // Bootstrap 자체에서 사용하는 starter 들.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ES Java Client 빈 등록 (ElasticsearchConfig 가 직접 RestClient/ElasticsearchClient 참조).
    implementation("co.elastic.clients:elasticsearch-java:8.15.3")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Resilience4j — 빈 customizer (CB / Retry / Bulkhead).
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    // Actuator + Prometheus.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}

tasks.named("bootJar") {
    enabled = true
}

// e2e-tests 가 SearchApplication 클래스를 import 할 수 있도록 plain jar 도 활성화.
// (Spring Boot 3 의 jar/bootJar 공존 — bootJar 가 실행파일, jar 가 라이브러리)
tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveClassifier.set("boot")
}
