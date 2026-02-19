# Local Development Dockerfile
# This Dockerfile is designed for local development with the app running locally
# and Redis/PostgreSQL running in Docker containers via docker-compose

# Use a lightweight base image with Java 17
FROM eclipse-temurin:17-jre-alpine

# Install Maven for local development
RUN apk add --no-cache maven

# Set working directory
WORKDIR /app

# Copy Maven files first for better layer caching
COPY pom.xml .
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Expose the application port
EXPOSE 8080

# Environment variables for local development
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=dev

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "target/SimplyDone-0.0.1-SNAPSHOT.jar"]
