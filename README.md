# SimplyDone — Priority Job Scheduler

SimplyDone is a production-ready, distributed background job scheduling system built with Spring Boot 3. It provides a Redis/Valkey-backed dual-priority queue, PostgreSQL state persistence, per-user rate limiting, automatic retry with exponential back-off, and a Thymeleaf web interface for managing the full job lifecycle.

**Live demo:** [simplydone-app.onrender.com](https://simplydone-app.onrender.com)

---

## Architecture Overview

```
Client (Browser / API)
        |
   REST Controllers  ← rate limiting enforced here (Redis fixed-window per userId)
        |
   Job Service  ← UUID assignment, validation, queue placement, PostgreSQL upsert
        |              |
   Redis Sorted Sets  PostgreSQL (durable job state + execution history)
        |
   Background Worker  (polls every 1 s, drains up to 5 jobs per tick)
        |
   Strategy Executor  ← EMAIL_SEND, API_CALL, DATA_PROCESS, FILE_OPERATION,
                         NOTIFICATION, REPORT_GENERATION, CLEANUP
```

Jobs are stored in Redis sorted sets scored by `executeAt` (Unix milliseconds). The worker polls the HIGH priority queue first, then LOW, and only dequeues entries whose score is at or below the current time. This enables both instant dispatch and scheduled (delayed) execution from the same queue.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Application | Java 17, Spring Boot 3.2 |
| Queue & Rate Limiting | Redis 7 / Valkey 8 (sorted sets + fixed-window counters) |
| Persistence | PostgreSQL 15+ (durable job state and history) |
| ORM | Hibernate / Spring Data JPA |
| Frontend | Thymeleaf, Vanilla JS (ES6), Custom CSS |
| Build | Maven 3.8 |
| Container | Docker, Render Blueprints |

---

## Job Types

| Type | Description |
|---|---|
| `EMAIL_SEND` | Sends an HTML email via Gmail SMTP (or custom per-job credentials). All emails include a "Sent by SimplyDone" branding footer. |
| `DATA_PROCESS` | Transforms, aggregates, or validates CSV/JSON data in-process. |
| `API_CALL` | Executes an outbound HTTP request (GET/POST/PUT/DELETE) with configurable headers, body, and retry. |
| `FILE_OPERATION` | Copies, moves, deletes, zips, unzips, or lists files on the server. |
| `NOTIFICATION` | Posts messages to Slack, Discord, Teams, Telegram, or a generic webhook. |
| `REPORT_GENERATION` | Generates HTML, CSV, JSON, or plain-text reports from structured data. |
| `CLEANUP` | Deletes old files, clears directories, archives aged files, or purges logs by age or pattern. |

---

## Quick Start (Docker Compose)

### Prerequisites

- Docker 24+ and Docker Compose 2+

```bash
# 1. Clone
git clone https://github.com/learnerview/SimplyDone.git
cd SimplyDone

# 2. Optional: copy env template for SMTP credentials
cp .env.template .env
# Edit .env and set SMTP_USERNAME / SMTP_PASSWORD / EMAIL_ENABLED=true

# 3. Start all services (app + PostgreSQL + Redis/Valkey)
docker compose up -d

# 4. Verify
curl http://localhost:8080/api/jobs/health
```

The web interface is available at `http://localhost:8080`.  
PostgreSQL is exposed on `localhost:5433`, Redis on `localhost:6380`.

---

## Quick Start (Maven, local)

### Prerequisites

- Java 17+, Maven 3.8+, Docker (for PostgreSQL and Redis)

```bash
# 1. Start infrastructure only
docker compose up -d db redis

# 2. Run the application (uses H2 by default if DATABASE_URL is not set)
mvn spring-boot:run

# Or with explicit database URLs:
DATABASE_URL=postgresql://postgres:postgres@localhost:5433/simplydone \
REDIS_URL=redis://localhost:6380 \
mvn spring-boot:run
```

> **Note:** `DATABASE_URL` must use the `postgresql://` scheme (not `jdbc:`). The `entrypoint.sh` script and `DatabaseUrlEnvironmentPostProcessor` convert it to a JDBC URL automatically.

---

## Production Deployment (Render)

SimplyDone ships with a `render.yaml` Blueprint that provisions the entire stack automatically.

1. Sign in to [render.com](https://render.com) and click **New → Blueprint**.
2. Connect the `learnerview/SimplyDone` repository.
3. Render provisions the web service, PostgreSQL, and the Valkey (Redis-compatible) instance.
4. Set the following in the **`simplydone-secrets`** environment group on Render:

| Variable | Description |
|---|---|
| `SMTP_USERNAME` | Gmail address used by `EMAIL_SEND` jobs |
| `SMTP_PASSWORD` | Gmail App Password (generated at myaccount.google.com/apppasswords) |
| `EMAIL_ENABLED` | `true` to enable live email dispatch |

Render injects `DATABASE_URL` and `REDIS_URL` automatically. The `entrypoint.sh` script parses both into the individual Spring properties that the app reads:

| URL | Parsed into |
|---|---|
| `DATABASE_URL` | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| `REDIS_URL` | `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`, `SPRING_DATA_REDIS_PASSWORD`, `SPRING_DATA_REDIS_SSL_ENABLED` |

A Java-side `RedisUrlEnvironmentPostProcessor` (and the existing `DatabaseUrlEnvironmentPostProcessor`) act as fallbacks in case the shell script didn't run.

---

## Key API Endpoints

```
POST   /api/jobs                          Submit a new job
GET    /api/jobs/{id}                     Retrieve job status and result
DELETE /api/jobs/{id}                     Cancel a pending job
GET    /api/jobs/health                   Health check (used by Render)
GET    /api/jobs/rate-limit/{userId}      Rate limit status for a user

GET    /api/admin/stats                   System statistics (executed, rejected, queue sizes)
GET    /api/admin/queues/{priority}       List jobs in a queue (high or low)
GET    /api/admin/health                  Detailed health including Redis and queue info
GET    /api/admin/performance             JVM memory + job processing metrics
GET    /api/admin/jobs/user/{userId}      All jobs submitted by a specific user
GET    /api/admin/jobs/executed           Completed job history from PostgreSQL (EXECUTED + FAILED)
GET    /api/admin/dead-letter-queue       Jobs that exhausted all retries
POST   /api/admin/dead-letter-queue/{id}/retry  Re-queue a DLQ job
DELETE /api/admin/queues/clear            Wipe all queues
```

See `docs/REST_API_REFERENCE.md` for full request/response examples.

---

## Web Interface Pages

| URL | Description |
|---|---|
| `/` | Dashboard — stats, queue tables, quick-submit form |
| `/jobs` | All jobs in both queues with live polling |
| `/job-status?id={id}` | Detailed trace for a single job |
| `/executed-jobs` | Historical view of completed and failed jobs from PostgreSQL |
| `/email-send` | Submit `EMAIL_SEND` jobs |
| `/data-process` | Submit `DATA_PROCESS` jobs |
| `/api-call` | Submit `API_CALL` jobs |
| `/file-operation` | Submit `FILE_OPERATION` jobs |
| `/notification` | Submit `NOTIFICATION` jobs |
| `/report-generation` | Submit `REPORT_GENERATION` jobs |
| `/cleanup` | Submit `CLEANUP` jobs |
| `/assets` | File upload vault |
| `/rate-limits` | Inspect per-user rate limit status and quota usage |
| `/system-health` | Live system diagnostics (health, JVM, queues, strategies) |
| `/dlq` | Dead Letter Queue — view, retry, or discard failed jobs |
| `/admin` | Admin panel — system vitals and DLQ management |

---

## Configuration

The most commonly overridden properties:

| Environment Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | (H2 in-memory) | PostgreSQL URL in `postgresql://USER:PASS@HOST:PORT/DB` format |
| `REDIS_URL` | `redis://localhost:6379` | Redis/Valkey URL; supports `redis://`, `redis://:pw@host`, `rediss://` (TLS) |
| `SMTP_USERNAME` | (empty) | Gmail address for email dispatch |
| `SMTP_PASSWORD` | (empty) | Gmail App Password |
| `EMAIL_ENABLED` | `false` | Master switch for email sending |
| `PORT` | `8080` | HTTP listening port |

Full property reference: `docs/SYSTEM_CONFIGURATION.md`

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


