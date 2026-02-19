# Build stage
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# Environment defaults
ENV DATABASE_URL=jdbc:postgresql://db:5432/simplydone
ENV DATABASE_USER=postgres
ENV DATABASE_PASSWORD=postgres
ENV REDIS_URL=redis://redis:6379
ENV PORT=8080

ENTRYPOINT ["java", "-jar", "app.jar"]
