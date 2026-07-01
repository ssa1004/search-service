# Multi-stage — JDK 21 build + JRE 21 runtime, non-root, ZGC.

# ── build stage ──
FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY search-domain ./search-domain
COPY search-application ./search-application
COPY search-adapter-in ./search-adapter-in
COPY search-adapter-out ./search-adapter-out
COPY search-bootstrap ./search-bootstrap
COPY e2e-tests ./e2e-tests
RUN ./gradlew :search-bootstrap:bootJar -x test --no-daemon

# ── runtime stage ──
FROM eclipse-temurin:25-jre-jammy

# OCI image labels — provenance/표준 메타데이터 (ghcr 패키지 페이지 + 스캐너가 읽음).
LABEL org.opencontainers.image.title="search-service" \
      org.opencontainers.image.description="Commerce 상품 검색 서비스 — Elasticsearch 기반 hexagonal, CDC + 동의어 hot reload + SavedSearch alert." \
      org.opencontainers.image.source="https://github.com/ssa1004/search-service" \
      org.opencontainers.image.url="https://github.com/ssa1004/search-service" \
      org.opencontainers.image.licenses="Apache-2.0" \
      org.opencontainers.image.vendor="ssa1004"

WORKDIR /app

# 베이스(jammy) 보안 패치 최신화 + HEALTHCHECK 용 curl 만 설치.
# DL3008: jammy 의 apt 는 base 갱신마다 패치 버전이 바뀌어 pin 이 깨지므로 의도적으로 비고정.
# hadolint ignore=DL3008
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/search-bootstrap/build/libs/search-bootstrap-*-boot.jar app.jar
USER app
EXPOSE 8080

# 컨테이너 자체 헬스 — k8s probe 와 별개로 docker/compose 가 readiness 를 본다.
# liveness actuator endpoint 가 200 이면 healthy.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

# ZGC — Java 21 의 generational ZGC. 검색 응답 jitter 최소화.
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx1g"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
