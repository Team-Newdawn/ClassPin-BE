# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew --no-daemon test bootJar

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends \
    libreoffice-impress poppler-utils fonts-nanum fonts-noto-cjk ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system ohpin && useradd --system --gid ohpin --create-home ohpin
WORKDIR /app
COPY --from=build --chown=ohpin:ohpin /workspace/build/libs/ohpin-be.jar /app/ohpin-be.jar
USER ohpin
ENV SPRING_PROFILES_ACTIVE=prod PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl --fail --silent http://localhost:${PORT}/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/ohpin-be.jar"]
