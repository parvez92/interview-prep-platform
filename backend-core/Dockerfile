# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:26-jdk-noble AS build

WORKDIR /app

# Copy Gradle wrapper first so wrapper download is cached when only source changes
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle ./gradle

# Download dependencies in a separate layer
RUN ./gradlew dependencies --no-daemon -q

# Now copy source and build the fat jar
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:26-jre-noble AS runtime

# Non-root user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# Create writable directory for file uploads
RUN mkdir -p /app/files && chown -R appuser:appgroup /app/files app.jar
USER appuser

EXPOSE 8080

# Spring Cloud Vault needs VAULT_TOKEN; spring.cloud.vault.host/port come from env
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
