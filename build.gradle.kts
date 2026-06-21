// 루트 빌드 — 공통 conventions. 각 모듈이 상속받는 공유 설정.
plugins {
    java
    id("org.springframework.boot") version "3.5.15" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    // Kotlin 은 도메인 모듈만 사용 — 루트에서는 버전만 고정하고 apply 는 모듈이 직접.
    kotlin("jvm") version "2.1.0" apply false
    // OpenAPI spec build-time export — 실제 적용은 bootstrap 모듈.
    id("org.springdoc.openapi-gradle-plugin") version "1.9.0" apply false
    // 코드 커버리지 — Kotlin 코드베이스라 JaCoCo 대신 Kover. 루트에서 aggregate.
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
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
    // 각 모듈의 test 실행을 계측 — 루트가 이 결과를 모아 aggregate report 를 만든다.
    apply(plugin = "org.jetbrains.kotlinx.kover")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.15")
        }
    }

    dependencies {
        // Gradle 8+ 부터 launcher 가 transitively 안 끌려옴 → 명시.
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    // excludeTags 는 default `test` 태스크에만 — withType<Test> 로 걸면 integrationTest 까지
    // 잡아 includeTags("integration") 를 덮어써 통합 테스트가 조용히 0건 실행된다.
    tasks.named<Test>("test") {
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

// 커버리지 aggregate — 루트가 production 모듈들의 계측 결과를 모은다.
// e2e-tests 는 production source 가 없고 Testcontainers 를 끌어오므로 집계 대상에서 제외.
// 리포트 생성: ./gradlew koverXmlReport koverHtmlReport
//   - XML  → build/reports/kover/report.xml (배지 / CI 파싱용)
//   - HTML → build/reports/kover/html/index.html
dependencies {
    kover(project(":search-domain"))
    kover(project(":search-application"))
    kover(project(":search-adapter-in"))
    kover(project(":search-adapter-out"))
    kover(project(":search-bootstrap"))
}

kover {
    reports {
        filters {
            excludes {
                // Spring Boot 진입점 / 생성 코드 — 단위 테스트 대상이 아니라 집계 노이즈.
                classes("com.example.search.SearchApplication*")
                annotatedBy("org.springframework.context.annotation.Configuration")
            }
        }
    }
}
