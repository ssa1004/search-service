// 순수 도메인. Spring 의존성 0. JPA / Elasticsearch 어노테이션도 0. (헥사고날 핵심)
// jakarta.validation 만 허용 — Bean Validation 어노테이션은 표준이고 프레임워크 비의존.
//
// 도메인 모델은 Kotlin 으로 작성 — record 는 @JvmRecord data class 로 매핑되어
// Java 호출자 (application / adapter) 에서 기존 record 접근자 그대로 사용 가능.
plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api("jakarta.validation:jakarta.validation-api")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

kotlin {
    // 루트 java toolchain (21) 과 일치 — @JvmRecord 는 JVM 16+ 에서 동작.
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        // Java 호출자가 record 스타일 접근자 (foo.value()) 를 쓰므로 @JvmRecord 활성 필수.
        // 2.x 에서 record 는 안정 기능이라 별도 preview flag 불필요.
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}
