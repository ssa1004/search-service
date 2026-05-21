// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
plugins {
    java
    id("org.springframework.boot") version "3.4.13" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // Kotlin 은 도메인 모듈만 사용 — 루트에서는 버전만 고정하고 apply 는 모듈이 직접.
    kotlin("jvm") version "2.1.0" apply false
    // OpenAPI spec build-time export — 실제 적용은 bootstrap 모듈.
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0" apply false
}

allprojects {
    group = "com.example.search"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.4.13")
        }
    }

    dependencies {
        // 모든 모듈 공통 — Lombok + JUnit launcher.
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        // Gradle 8+ 부터 launcher 가 transitively 안 끌려옴 → 명시.
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            // ./gradlew test 는 단위만 실행. integration 태그는 별도 task 로 격리.
            excludeTags("integration")
        }
        // 통합 테스트는 ./gradlew integrationTest 로 명시 실행 (Testcontainers 가 무거워 default skip).
    }

    tasks.register<Test>("integrationTest") {
        description = "@Tag(\"integration\") 만 실행 — Testcontainers (Postgres + Kafka + Elasticsearch)"
        group = "verification"
        useJUnitPlatform {
            includeTags("integration")
        }
        shouldRunAfter("test")
        // Testcontainers 가 메모리를 많이 씀 — heap 명시.
        maxHeapSize = "2g"
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all", "-Xlint:-processing"))
        options.encoding = "UTF-8"
    }
}
