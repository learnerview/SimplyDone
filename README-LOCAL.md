# Local Development Setup

This setup allows you to run the SimplyDone application locally while keeping PostgreSQL and Redis in Docker containers.

## Quick Start

### 1. Start Database Services
```bash
# Start only PostgreSQL and Redis
docker-compose up postgres redis -d
```

### 2. Run Application Locally
```bash
# Build and run the application locally
mvn clean package -DskipTests
java -jar target/SimplyDone-0.0.1-SNAPSHOT.jar
```

Or run with Maven:
```bash
mvn spring-boot:run
```

### 3. Access the Application
- Application: http://localhost:8080
- Health Check: http://localhost:8080/actuator/health
- PostgreSQL: localhost:5433
- Redis: localhost:6380

## Alternative: Run Everything in Docker

If you prefer to run the entire stack in Docker:

```bash
# Start all services including the application
docker-compose --profile containerized up -d
```

## Environment Variables

Create a `.env` file for SMTP configuration:
```
SMTP_USERNAME=your-gmail-username
SMTP_PASSWORD=your-app-password
```

## Development Workflow

1. Make code changes
2. Restart the Spring Boot application (Ctrl+C, then run again)
3. Database and Redis continue running in Docker
4. No need to rebuild containers unless you change dependencies

## Stopping Services

```bash
# Stop database services
docker-compose down

# Stop all services (including app if running in container)
docker-compose --profile containerized down
```

## Database Management

- Connect to PostgreSQL: `psql -h localhost -p 5433 -U postgres -d simplydone`
- Connect to Redis: `redis-cli -h localhost -p 6380`

## Troubleshooting

- If PostgreSQL doesn't start: Check port 5433 is not in use
- If Redis doesn't start: Check port 6380 is not in use
- Application can't connect to database: Ensure database services are running first
