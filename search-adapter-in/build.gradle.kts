// Inbound adapter — REST controllers + Kafka consumers (CDC).
// Application 의 UseCase 인터페이스만 호출.
//
// Kotlin 으로 마이그레이션 — Spring MVC controller / Kafka @KafkaListener 클래스가 AOP proxy
// 대상이 될 수 있도록 plugin.spring 으로 @Component / @RestController / @KafkaListener 자동 open.
plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.spring") version "2.3.21"
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
