# Getting Started with SimplyDone

This guide provides a comprehensive overview of the setup and initialization process for the SimplyDone Priority Job Scheduler.

## System Prerequisites

| Requirement | Version | Validation Command | Notes |
|---|---|---|---|
| Java | 17+ | `java -version` | Core execution environment |
| Maven | 3.8+ | `mvn -version` | Build and dependency management |
| Docker | 24+ | `docker --version` | Container runtime for infrastructure |
| Docker Compose | 2.0+ | `docker compose version` | Infrastructure orchestration |

## 1. Repository Initialization

Begin by cloning the source code to your local development environment:

```bash
git clone <repository-url>
cd SimplyDone
```

## 2. Infrastructure Deployment

SimplyDone relies on PostgreSQL for persistence and Redis for high-throughput queuing. Use the optimized `docker-compose.yml` to initialize these services:

```bash
docker compose up -d
```

### Service Specifications:
- **PostgreSQL 15**: Accessible on port **5433**.
  - Database: `simplydone`
  - User: `postgres`
  - Password: `postgres` (Recommend updating for production)
- **Redis 7**: Accessible on port **6380**.
  - High-performance transient data store.

## 3. Application Build Process

Utilize Maven to compile and package the application:

```bash
mvn clean install -DskipTests
```

This process downloads all requisite dependencies from Maven Central and prepares the application JAR in the `target/` directory.

## 4. Execution

Launch the application using the Spring Boot Maven plugin:

```bash
mvn spring-boot:run
```

Upon successful initialization, the application will be accessible at `http://localhost:8080`.

## 5. System Verification

### Dashboard Access
Navigate to `http://localhost:8080` to access the real-time monitoring dashboard.

### Health Audit
Verify system integrity via the actuator endpoint:
```bash
curl http://localhost:8080/actuator/health
```

### Initial Job Submission
Submit a test job to verify the end-to-end pipeline:
```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "message": "System verification job",
    "priority": "HIGH",
    "userId": "admin",
    "jobType": "API_CALL",
    "parameters": {
      "url": "https://httpbin.org/get",
      "method": "GET"
    }
  }'
```

## Next Steps
- Consult the [REST API Reference](REST_API_REFERENCE.md) for endpoint details.
- Review the [User Interface Guide](USER_INTERFACE_GUIDE.md) for dashboard operations.
- Reference the [System Configuration](SYSTEM_CONFIGURATION.md) for environment tuning.
