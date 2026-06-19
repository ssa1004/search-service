// Spring Boot 진입점. main + 통합 config + Flyway + ES mapping init.
//
// Kotlin 으로 마이그레이션. @Configuration / @Component 클래스의 AOP proxy 화를 위해 plugin.spring 으로
// 자동 open 처리한다. @SpringBootApplication 진입점도 Kotlin top-level fun main + class 패턴.
plugins {
    java
    kotlin("jvm")
    kotlin("plugin.spring") version "2.1.0"
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    // OpenAPI spec build-time export — generateOpenApiDocs 가 앱을 부팅한 뒤
    // /v3/api-docs 를 fetch 해 docs/openapi/search-service.yaml 로 떨어뜨린다.
    id("org.springdoc.openapi-gradle-plugin")
}

dependencies {
    implementation(project(":search-domain"))
    implementation(project(":search-application"))
    implementation(project(":search-adapter-in"))
    implementation(project(":search-adapter-out"))

    // Kotlin runtime — @ConfigurationProperties data class binding 의 PreferredConstructorDiscoverer 가
    // kotlin.reflect 를 호출한다.
    implementation(kotlin("reflect"))
    // Spring Boot Jackson auto config 가 detect 하여 Kotlin data class 의 deserialization 을 지원.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Bootstrap 자체에서 사용하는 starter 들.
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ES Java Client 빈 등록 (ElasticsearchConfig 가 직접 RestClient/ElasticsearchClient 참조).
    implementation("co.elastic.clients:elasticsearch-java:8.15.5")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Resilience4j — 빈 customizer (CB / Retry / Bulkhead).
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

    // ShedLock — bootstrap 에서 LockProvider 빈 등록.
    implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.16.0")

    // Kafka — KafkaConfig 가 ConsumerFactory / ContainerFactory 빈을 직접 정의.
    implementation("org.springframework.kafka:spring-kafka")

    // Actuator + Prometheus.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:elasticsearch")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        // Java 호출자가 record 스타일 접근자 (foo.value()) 를 쓰므로 @JvmRecord 활성 필수.
        // 인터페이스 default 메서드도 Java 측에 그대로 노출되도록 -Xjvm-default=all.
        freeCompilerArgs.add("-Xjvm-default=all")
    }
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

// OpenAPI spec export 설정 — ./gradlew :search-bootstrap:generateOpenApiDocs.
// 플러그인이 bootRun 으로 앱을 띄우고 apiDocsUrl 을 fetch 해 outputFileName 으로 저장한다.
//
// 메모리 모드(SEARCH_ENGINE=memory)로 띄우므로 Docker / Postgres / Kafka / ES 없이도
// spec 을 생성할 수 있다 — datasource 는 H2(in-memory), Kafka 는 비활성, 검색은
// InMemorySearchEngineAdapter. REST 컨트롤러 매핑은 엔진과 무관하게 동일하게 노출된다.
openApi {
    apiDocsUrl.set("http://localhost:8080/v3/api-docs.yaml")
    outputDir.set(layout.projectDirectory.dir("../docs/openapi"))
    outputFileName.set("search-service.yaml")
    waitTimeInSeconds.set(120)
}

// generateOpenApiDocs 가 forking 하는 bootRun 을 메모리 모드로 강제 — 외부 인프라 불필요.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    environment("SEARCH_ENGINE", System.getenv("SEARCH_ENGINE") ?: "memory")
    environment("SEARCH_KAFKA_ENABLED", System.getenv("SEARCH_KAFKA_ENABLED") ?: "false")
}

// openapi-gradle-plugin 1.9.0 + Gradle 8.x 호환 — forkedSpringBootRun 이 의존 모듈 jar 를
// 입력으로 쓰면서 task 의존을 선언하지 않아 strict validation 으로 BUILD FAILED 가 난다.
// 명시적 dependsOn 으로 jar 산출 순서를 보장.
tasks.matching { it.name == "forkedSpringBootRun" }.configureEach {
    dependsOn(
        ":search-domain:jar",
        ":search-application:jar",
        ":search-adapter-in:jar",
        ":search-adapter-out:jar",
    )
}
