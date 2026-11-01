# syntax=docker/dockerfile:1
# Self-contained build: no local Java, Gradle or pre-built jar needed.
#
#   docker build -t secure-auth/auth-service .
#   docker run --rm -p 8081:8081 --read-only --tmpfs /tmp --cap-drop ALL \
#       --security-opt no-new-privileges:true secure-auth/auth-service
#
# Then open http://localhost:8081

# ---- Stage 1: compile the application ----
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /src

# Copy build definitions first so the dependency download layer is cached between source changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY auth-service/build.gradle.kts auth-service/gradle.lockfile auth-service/
RUN chmod +x gradlew && ./gradlew --no-daemon :auth-service:dependencies > /dev/null

COPY auth-service/src auth-service/src
ARG APP_VERSION=0.0.0-local
RUN ./gradlew --no-daemon :auth-service:bootJar -x test -PappVersion="${APP_VERSION}"

# ---- Stage 2: minimal runtime image ----
FROM eclipse-temurin:17-jre-alpine

# Pull in OS security fixes on top of the base image, then create a dedicated non-root user with fixed IDs.
RUN apk upgrade --no-cache \
    && addgroup -S -g 10001 app \
    && adduser -S -u 10001 -G app app

WORKDIR /app
COPY --from=build --chown=10001:10001 /src/auth-service/build/libs/app.jar /app/app.jar

ENV SERVER_PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8081
USER 10001:10001

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
