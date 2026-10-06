# Stage 1: Build stage with Maven and Amazon Corretto 21
FROM maven:3.9-amazoncorretto-21 AS builder
WORKDIR /build

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and package shaded fat JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime stage with Amazon Corretto 21 (Alpine / Slim runtime)
FROM amazoncorretto:21-alpine
WORKDIR /app

# Create non-root security user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy shaded application JAR from builder stage
COPY --from=builder /build/target/mini-web-framework-1.0.0.jar app.jar

# Set file ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser:appgroup

# Environment defaults
ENV PORT=8080 \
    APP_ENV=production \
    GREETING_PREFIX=Hello \
    STATIC_FILES_PATH="" \
    THREAD_POOL_SIZE=16 \
    SHUTDOWN_TIMEOUT_SECONDS=10

EXPOSE 8080

# Exec form ENTRYPOINT ensures SIGTERM from docker stop propagates directly to the JVM
ENTRYPOINT ["java", "-jar", "app.jar"]
