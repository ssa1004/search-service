# Multi-stage — JDK 21 build + JRE 21 runtime, non-root, ZGC.

# ── build stage ──
FROM eclipse-temurin:21-jdk-jammy AS build
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
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /workspace/search-bootstrap/build/libs/search-bootstrap-*-boot.jar app.jar
USER app
EXPOSE 8080
# ZGC — Java 21 의 generational ZGC. 검색 응답 jitter 최소화.
ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx1g"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
