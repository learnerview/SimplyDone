# SimplyDone

A production-ready priority job scheduling system built with Spring Boot 3.2 and Java 17. SimplyDone accepts job submissions via REST API, queues them in Redis with HIGH and LOW priority lanes, executes them using a pluggable strategy pattern, persists the audit trail in PostgreSQL, and includes a browser-based dashboard for monitoring and control.

## Overview

SimplyDone solves asynchronous job processing with a clean separation between submission, queueing, and execution. Submit a job via REST endpoint or web form, the scheduler holds it in Redis, a background worker dispatches it to the correct handler when its execution time arrives, and all events are logged permanently to PostgreSQL.

### Key features

- **Priority queues** — HIGH and LOW priority lanes with independent processing
- **7 pluggable job types** — API calls, email, reports, data processing, file operations, notifications, cleanup
- **Resilient execution** — Retry logic with exponential backoff, dead-letter queue for permanent failures
- **Complete audit trail** — Every submission, execution, and failure logged to PostgreSQL
- **Web dashboard** — Browser-based interface for job submission and queue monitoring
- **REST API** — Full CRUD operations with job lifecycle management
- **File upload** — Drag-and-drop file upload with automatic cleanup scheduler
- **Docker ready** — Includes docker-compose for local development
- **Cloud native** — Production-ready configuration for Render, AWS, GCP, or Azure

## Supported job types

| Type | Purpose | Use case |
|---|---|---|
| **EMAIL_SEND** | Sends transactional email via Resend API | Automated notifications, password resets |
| **DATA_PROCESS** | Reads CSV, transforms, aggregates, validates | ETL pipelines, data cleanup |
| **API_CALL** | Makes HTTP requests with retry logic | Webhooks, integrations, sync operations |
| **FILE_OPERATION** | Copy, move, delete, zip, unzip files | File management, backups, archiving |
| **NOTIFICATION** | Posts to Slack, Discord, Teams, Telegram, webhooks | Alerts, status updates, team notifications |
| **REPORT_GENERATION** | Generates HTML, CSV, JSON, or text reports | Business intelligence, analytics, exports |
| **CLEANUP** | Deletes old files, clears directories, purges cache | Maintenance, disk cleanup, cache management |

## Quick start

### With Docker (recommended for local development)

**Prerequisites:** Docker, Docker Compose, Java 17, Maven 3.8+

```bash
# 1. Start PostgreSQL and Redis containers
docker compose up -d

# 2. Run the application
mvn spring-boot:run

# 3. Open the dashboard
# http://localhost:8080
```

### Without Docker (requires PostgreSQL and Redis)

```bash
# Set up PostgreSQL on localhost:5432, database: simplydone, user: postgres, password: postgres
# Set up Redis on localhost:6379

mvn spring-boot:run
```

### Submit your first job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Call httpbin API",
    "priority": "HIGH",
    "delay": 0,
    "userId": "user-1",
    "jobType": "API_CALL",
    "parameters": {
      "url": "https://httpbin.org/get",
      "method": "GET"
    }
  }'
```

Response includes the job ID:
```json
{
  "id": "job-abc123",
  "status": "SUBMITTED",
  "submittedAt": "2024-01-15T10:30:00Z",
  "executeAt": "2024-01-15T10:30:00Z",
  "message": "Job submitted successfully"
}
```

## Technology stack

| Component | Version | Purpose |
|---|---|---|
| **Java** | 17+ | Runtime environment |
| **Spring Boot** | 3.2.1 | Web framework, dependency injection |
| **Spring Data Redis** | Latest | Redis client and repository abstraction |
| **Spring Data JPA** | Latest | PostgreSQL ORM with Hibernate |
| **PostgreSQL** | 15+ | Persistent audit trail |
| **Redis** | 7+ | Priority queues and job state |
| **Thymeleaf** | Latest | Server-side HTML templating |
| **Resend** | API v1 | Transactional email delivery |
| **Micrometer** | Latest | Metrics and observability |

## Documentation

| Document | Contents |
|---|---|
| [Getting Started](docs/01-getting-started.md) | Local setup, Docker, first run, verification |
| [Web UI Guide](docs/02-web-ui-guide.md) | Dashboard, pages, job submission forms, monitoring |
| [API Reference](docs/03-api-reference.md) | Complete REST endpoint documentation with examples |
| [Job Types](docs/04-job-types.md) | Parameters, examples, and use cases for all 7 job types |
| [Configuration](docs/05-configuration.md) | All configuration properties, environment variables, profiles |
| [Deployment Guide](docs/06-deployment-guide.md) | Deploying to Render, AWS, Docker, production checklist |
| [Architecture](docs/07-architecture.md) | System design, data flow, component interactions |
| [Troubleshooting](docs/08-troubleshooting.md) | Common issues, debugging, health checks, logs |

## Project structure

```
SimplyDone/
├── src/main/java/com/learnerview/SimplyDone/
│   ├── config/              Spring configuration classes
│   ├── controller/          REST controllers, view routing
│   ├── dto/                 Request/response DTOs
│   ├── entity/              JPA entities (PostgreSQL schema)
│   ├── exception/           Custom exceptions, global error handler
│   ├── model/               Domain models, enums
│   ├── repository/          Redis and JPA repository interfaces
│   ├── service/             Business logic layer
│   │   ├── impl/            Service implementations
│   │   └── strategy/        Job execution strategies (7 types)
│   └── worker/              Background job processing thread
│
├── src/main/resources/
│   ├── static/              CSS, JavaScript assets
│   │   ├── css/             Styling (glassmorphism, responsive)
│   │   └── js/              Frontend scripts, API client
│   ├── templates/           Thymeleaf HTML pages
│   │   ├── fragments/       Reusable fragments (head, sidebar)
│   │   └── *.html           11 user-facing pages + admin
│   │
│   ├── application.properties          Core configuration
│   ├── application-local.properties    Local development profile
│   └── application-prod.properties     Production profile (Render)
│
├── docs/                    Documentation
├── scripts/                 PowerShell/Shell setup scripts
├── Dockerfile               Multi-stage build for production
├── docker-compose.yml       Local development services
└── render.yaml              Render infrastructure blueprint
```

## Design patterns

### Strategy Pattern (Job Execution)

Each job type implements a strategy interface:
```java
public interface JobStrategy {
    JobResult execute(JobRequest request);
}
```

Seven implementations exist for the seven job types. When a job is dequeued, the worker looks up the appropriate strategy and executes it.

### Repository Pattern (Data Access)

Spring Data JPA and Redis repositories provide abstraction over underlying storage:
- `JobRepository` → PostgreSQL
- `JobQueueRepository` → Redis sorted sets
- `DeadLetterQueueRepository` → Redis lists

Applications access data through repository interfaces, not raw SQL or Redis commands.

### Observer Pattern (Async Events)

Job events (submission, execution, failure) trigger ApplicationEvent publication. Listeners can subscribe to react to events without tight coupling.

## Deployment

### Local development with Docker
```bash
docker compose up -d
mvn spring-boot:run
```

### Render PaaS (recommended for production)
See [Deployment Guide](docs/06-deployment-guide.md) for step-by-step instructions. TL;DR:
```bash
git push                    # Push to repository
```
Render automatically builds from render.yaml blueprint, provisions PostgreSQL + Redis, deploys the container, and enables auto-scaling.

### Self-hosted Docker
```bash
docker build -t simplydone .
docker run -e SPRING_PROFILES_ACTIVE=prod \
           -e DATABASE_URL=... \
           -e REDIS_URL=... \
           -p 8080:8080 \
           simplydone
```

## Testing

### Health check
```bash
curl http://localhost:8080/actuator/health
```

### Run comprehensive test suite
```powershell
# Windows PowerShell
.\scripts\test-comprehensive.ps1 -BaseUrl "http://localhost:8080"
```

### Run via Maven
```bash
mvn test
```

## Monitoring

### Actuator endpoints
- `/actuator/health` — Live readiness probe
- `/actuator/metrics` — JVM, HTTP, database metrics
- `/actuator/info` — Application version and environment

### Dashboard metrics
- System health (Redis, PostgreSQL connections)
- Queue depths (HIGH and LOW priority)
- Total jobs executed (lifetime counter)
- Dead-letter queue size
- Worker status and polling interval

### Logs
Check `logs_debug.txt` for application trace logs during development.

## Environment variables

### Required for Render/cloud deployment
```bash
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=postgresql://user:password@host:5432/database
REDIS_URL=redis://host:6379
EMAIL_API_KEY=your_resend_api_key  # If email is enabled
```

### Optional
```bash
PORT=8080
SPRING_APPLICATION_NAME=SimplyDone
EMAIL_ENABLED=true|false
```

See [Configuration](docs/05-configuration.md) for complete reference.

## Common tasks

### Submit a job via curl
```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"message":"Job name","priority":"HIGH","userId":"user-1","jobType":"API_CALL","parameters":{"url":"https://api.example.com/endpoint","method":"GET"}}'
```

### Check job status
```bash
curl http://localhost:8080/api/jobs/job-abc123
```

### View high-priority queue
```bash
curl http://localhost:8080/api/admin/queues/high
```

### Retry a dead-letter job
```bash
curl -X POST http://localhost:8080/api/admin/dead-letter-queue/retry/job-abc123
```

### Upload a file
- Via Web UI: Visit http://localhost:8080, drag and drop file to upload
- Via API: See [API Reference — File Upload](docs/03-api-reference.md#file-upload-endpoints)

## License

SimplyDone is provided under the terms specified in the LICENSE file.

## Support

For questions, bug reports, or feature requests, please refer to the documentation and troubleshooting guide. Common issues are documented in [Troubleshooting](docs/08-troubleshooting.md).
