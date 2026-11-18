# syntax=docker/dockerfile:1
# treasury-service (docs/architecture.md, section 14): build with the Gradle wrapper on JDK 21,
# run on the JRE as non-root user. The image build does not run tests.

FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /workspace

# Build scripts first, so that source changes do not invalidate the wrapper layer.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY rules/build.gradle.kts rules/
COPY rules/src/main rules/src/main
COPY src/main src/main

# The toolchain (Java 21) matches the image JDK, so foojay does not download anything.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --console=plain bootJar \
    && cp build/libs/treasury-service-*.jar /workspace/app.jar

FROM eclipse-temurin:21.0.13_9-jre-noble
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
