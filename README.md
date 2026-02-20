# SimplyDone - Priority Job Scheduler

SimplyDone is a production-ready, distributed background job scheduling system built with Spring Boot 3. It provides a Redis-backed priority queue, PostgreSQL state persistence, per-user rate limiting, and a Thymeleaf web interface for managing the full job lifecycle.

---

## Architecture Overview

```
Client (Browser / API)
        |
   REST Controllers  <-- rate limiting enforced here
        |
   Job Service  <-- UUID assignment, validation, queue placement
        |              |
   Redis (Sorted Sets)  PostgreSQL (job status, history)
        |
   Background Worker (polls every 1 s)
        |
   Strategy Executor  <-- EMAIL_SEND, API_CALL, DATA_PROCESS, etc.
```

Jobs are stored in Redis sorted sets scored by `executeAt` (Unix milliseconds). The worker polls the HIGH priority queue first, then LOW, and only dequeues entries whose score is at or below the current time. This enables both instant dispatch and scheduled (delayed) execution from the same queue.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Application | Java 17, Spring Boot 3.2 |
| Persistence | PostgreSQL 15 (state), Redis 7 (queues and rate limiting) |
| ORM | Hibernate / Spring Data JPA |
| Frontend | Thymeleaf, Vanilla JS (ES6), Custom CSS |
| Build | Maven 3.8 |
| Deployment | Docker, Render Blueprints |

---

## Job Types

| Type | Description |
|---|---|
| EMAIL_SEND | Sends an HTML email via Gmail SMTP (or custom credentials) |
| DATA_PROCESS | Transforms, aggregates, or validates CSV/JSON data |
| API_CALL | Executes an outbound HTTP request with retry logic |
| FILE_OPERATION | Copies, moves, deletes, zips, unzips, or lists files |
| NOTIFICATION | Posts messages to Slack, Discord, Teams, Telegram, or a generic webhook |
| REPORT_GENERATION | Generates HTML, CSV, JSON, or plain-text reports from structured data |
| CLEANUP | Deletes old files, clears directories, or purges logs by age or pattern |

---

## Quick Start (Local, Docker Compose)

### Prerequisites

- Docker 24 or later
- Docker Compose 2.0 or later

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/learnerview/SimplyDone.git
cd SimplyDone

# 2. Create a local environment file
cp .env.template .env
# Edit .env if you want to enable email sending

# 3. Start all services (app + PostgreSQL + Redis)
docker compose up -d

# 4. Verify the application is running
curl http://localhost:8080/api/jobs/health
```

The web interface is available at `http://localhost:8080`.

---

## Quick Start (Local, Maven)

### Prerequisites

- Java 17 or later
- Maven 3.8 or later
- Docker (for PostgreSQL and Redis)

### Steps

```bash
# 1. Start infrastructure only
docker compose up -d db redis

# 2. Build and run the application
mvn spring-boot:run
```

The application connects to PostgreSQL on port 5433 and Redis on port 6380 as configured in `docker-compose.yml`.

---

## Production Deployment (Render)

SimplyDone includes a `render.yaml` Blueprint. Connect your repository to Render, select the Blueprint, and the entire stack (application, PostgreSQL, Redis) is provisioned automatically.

Set the following environment variables in the `simplydone-secrets` group on Render:

| Variable | Description |
|---|---|
| `SMTP_USERNAME` | Gmail address used for email jobs |
| `SMTP_PASSWORD` | Gmail App Password (not your account password) |
| `EMAIL_ENABLED` | Set to `true` to enable email sending |

---

## Key API Endpoints

```
POST   /api/jobs              Submit a new job
GET    /api/jobs/{id}         Retrieve job status and result
DELETE /api/jobs/{id}         Cancel a pending job
GET    /api/jobs/health       Health check (used by Render)

GET    /api/admin/stats              System statistics
GET    /api/admin/queues/{priority}  List jobs in a queue (high or low)
GET    /api/admin/health             Detailed health status
GET    /api/admin/dead-letter-queue  Jobs that exhausted retries
```

See `docs/REST_API_REFERENCE.md` for full request/response details.

---

## Documentation

| Document | Description |
|---|---|
| [Getting Started](docs/GETTING_STARTED.md) | Full setup walkthrough with verification steps |
| [REST API Reference](docs/REST_API_REFERENCE.md) | All endpoints with request/response examples |
| [Technical Architecture](docs/TECHNICAL_ARCHITECTURE.md) | Data flow, design patterns, and component roles |
| [System Configuration](docs/SYSTEM_CONFIGURATION.md) | All properties and environment variables |
| [Job Type Catalog](docs/JOB_TYPE_CATALOG.md) | Parameters and behavior for each job type |
| [User Interface Guide](docs/USER_INTERFACE_GUIDE.md) | Dashboard pages and submission workflows |
| [Deployment and Operations](docs/DEPLOYMENT_OPERATION_GUIDE.md) | Docker, Render, scaling, and disaster recovery |
| [Troubleshooting](docs/TROUBLESHOOTING_MAINTENANCE.md) | Common failure modes and solutions |
| [API Development Standards](docs/API_DEVELOPMENT_STANDARDS.md) | Coding conventions for contributors |
| [Plugin Development Guide](docs/PLUGIN_DEVELOPMENT_GUIDE.md) | Adding custom job strategies |

