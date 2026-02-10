# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy only pom.xml first to leverage Docker layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage — JRE for smaller image (~200MB vs ~400MB JDK)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy built jar from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose application port
EXPOSE 8080

# JVM flags for Render free tier (512MB RAM)
# JAVA_TOOL_OPTIONS can also be set via Render env var to override
ENV JAVA_TOOL_OPTIONS="-Xmx384m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"

ENTRYPOINT ["java", "-jar", "app.jar"]
