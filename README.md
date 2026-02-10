# SimplyDone — Generic Job Scheduling Engine

SimplyDone is a production-ready, self-hosted job scheduling engine built with Java 17 and Spring Boot 3.2.1. It provides priority-based job queuing, distributed execution via Redis, durable persistence via PostgreSQL, retry with exponential backoff, a dead letter queue, workflow (DAG) support, and a real-time browser dashboard powered by Server-Sent Events.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Technology Stack](#technology-stack)
3. [Project Structure](#project-structure)
4. [Getting Started](#getting-started)
   - [Prerequisites](#prerequisites)
   - [Local Development with H2](#local-development-with-h2)
   - [Local Development with Docker Compose](#local-development-with-docker-compose)
5. [Deploying to Render](#deploying-to-render)
6. [Configuration Reference](#configuration-reference)
7. [API Reference](#api-reference)
   - [Job Endpoints](#job-endpoints)
   - [Admin Endpoints](#admin-endpoints)
   - [SSE Event Stream](#sse-event-stream)
   - [Keep-Alive Endpoint](#keep-alive-endpoint)
8. [Job Types and Payloads](#job-types-and-payloads)
9. [Writing a Custom Job Handler](#writing-a-custom-job-handler)
10. [Workflow (DAG) Jobs](#workflow-dag-jobs)
11. [Priority Queuing](#priority-queuing)
12. [Retry and Exponential Backoff](#retry-and-exponential-backoff)
13. [Dead Letter Queue](#dead-letter-queue)
14. [Rate Limiting](#rate-limiting)
15. [Real-Time Dashboard](#real-time-dashboard)
16. [Keep-Alive for Render Free Tier](#keep-alive-for-render-free-tier)
17. [Monitoring and Metrics](#monitoring-and-metrics)

---

## Architecture Overview

```
Browser
  |
  |-- HTTP (REST)  --> JobController    --> JobSubmissionService
  |                                           |
  |-- SSE stream   --> SseController    --> SseEmitterService (broadcasts events)
  |                                           ^
  |-- View pages   --> ViewController         |
                                              |
                         SchedulerEngine ---> JobExecutorService
                              |                     |
                     RedisQueueRepository     RetryService
                              |                     |
                           Redis            JobEntityRepository
                                                    |
                                               PostgreSQL
```

**Request flow for a single job:**

1. Client posts `POST /api/jobs` with a job type, priority, user ID, and JSON payload.
2. `JobSubmissionService` validates the request, applies rate limiting, persists a `JobEntity` with status `QUEUED`, pushes the job ID onto the appropriate Redis priority queue, and broadcasts a `JOB_CREATED` SSE event to all connected browsers.
3. `SchedulerEngine` polls Redis every configurable interval (default 1 second) using Deficit Round-Robin weighted scheduling across HIGH, NORMAL, and LOW queues.
4. When a job ID is dequeued, `JobExecutorService` loads the entity from PostgreSQL, sets status to `RUNNING`, runs the registered `JobHandler`, and sets status to `SUCCESS`. It broadcasts `JOB_STARTED` and `JOB_COMPLETED` events.
5. On handler failure, `RetryService` schedules a retry with exponential backoff or moves the job to the Dead Letter Queue after maximum attempts are exhausted. It broadcasts `JOB_RETRY` or `JOB_FAILED` events.

---

## Technology Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.1 |
| Queue | Redis 7 (via Spring Data Redis / Lettuce) |
| Database | PostgreSQL 16 (production) / H2 (local dev) |
| Templates | Thymeleaf |
| Build | Maven 3.9 |
| Container | Docker (multi-stage, eclipse-temurin:17-jre-jammy) |
| Metrics | Micrometer + Prometheus |
| Real-time | Server-Sent Events (SSE) |

---

## Project Structure

```
src/main/java/com/learnerview/simplydone/
  config/
    DatabaseConfig.java       Parses Render's postgres:// DATABASE_URL into HikariCP DataSource
    RedisConfig.java          Parses REDIS_URL including rediss:// TLS format
    JacksonConfig.java        ISO-8601 date serialization
    SchedulerProperties.java  @ConfigurationProperties for scheduler tuning
  controller/
    JobController.java        POST /api/jobs, GET /api/jobs, GET /api/jobs/{id}, DELETE /api/jobs/{id}
    AdminController.java      GET|DELETE /api/admin/queues, GET /api/admin/stats, DLQ endpoints
    SseController.java        GET /api/events  (text/event-stream)
    PingController.java       GET /ping  (no DB/Redis — ultra-light keep-alive)
    ViewController.java       Thymeleaf page routes
  demo/
    EchoJobHandler.java       Built-in handler: echoes the 'message' field
    DelayJobHandler.java      Built-in handler: sleeps for 'seconds'
    HttpCallJobHandler.java   Built-in handler: makes outbound HTTP requests
  dto/                        Request/response DTOs
  entity/
    JobEntity.java            JPA entity — persisted job state
    JobExecutionLog.java      Audit log of each execution attempt
  handler/
    JobHandler.java           Interface all handlers implement
    JobContext.java           Passed to the handler: id, jobType, payload, attempt
    JobResult.java            SUCCESS or FAILURE with a message
    JobHandlerRegistry.java   Spring-managed map of jobType -> JobHandler
  model/
    JobStatus.java            QUEUED, RUNNING, SUCCESS, FAILED, DLQ
    JobPriority.java          HIGH, NORMAL, LOW
  repository/
    JobEntityRepository.java  JPA + JPQL query methods
    RedisQueueRepository.java LPUSH/BRPOP operations on priority lists
  service/
    SchedulerEngine.java      Deficit Round-Robin polling loop
    JobSubmissionService.java Validates, persists, enqueues
    JobExecutorService.java   Runs handler, updates status
    RetryService.java         Exponential backoff + DLQ promotion
    AdminService.java         Stats, queue management, DLQ operations
    WorkflowService.java      Resolves DAG dependencies, submits chain
    RateLimiterService.java   Sliding-window rate limiter (Redis)
    DependencyResolver.java   Topological sort for workflow DAG
    SseEmitterService.java    SSE broadcast to all connected clients

src/main/resources/
  application.properties      All configuration with env var fallbacks
  templates/                  Thymeleaf HTML pages
  static/css/style.css        Dark-themed UI stylesheet
  static/js/app.js            Frontend logic (SSE, toasts, filters, CSV export)
```

---

## Getting Started

### Prerequisites

- Java 17 or higher (`java -version`)
- Maven 3.9 or higher (`mvn -version`)
- Docker Desktop (for the Docker Compose path)

### Local Development with H2

No database or Redis installed is required. The application falls back to H2 in-memory and will fail to connect Redis — meaning jobs will be accepted but the scheduler cannot dequeue them without Redis. This mode is suitable only for inspecting the UI or API shape.

For a fully functional local environment use Docker Compose (see below).

**Steps:**

```bash
git clone https://github.com/your-org/simplydone.git
cd simplydone
mvn spring-boot:run
```

The application starts on `http://localhost:8080`.

### Local Development with Docker Compose

This starts the Spring Boot application, PostgreSQL 16, and Redis 7 together.

```bash
docker compose up --build
```

The build uses a multi-stage Dockerfile:
- Stage 1: Maven downloads dependencies (cached on subsequent builds), compiles the source, and produces the fat JAR.
- Stage 2: The JAR is copied into a minimal JRE image (`eclipse-temurin:17-jre-jammy`).

The application is available at `http://localhost:8080` once the healthchecks for PostgreSQL and Redis pass (typically 30-60 seconds on first run).

To stop and remove all containers:

```bash
docker compose down
```

To stop and also delete the persistent volumes (all data erased):

```bash
docker compose down -v
```

To rebuild after a code change:

```bash
docker compose up --build -d
```

To tail application logs:

```bash
docker compose logs -f app
```

---

## Deploying to Render

Render is the recommended cloud host. The free tier provides one web service and one Redis instance at no cost, with a managed PostgreSQL available on a paid starter plan.

**Step 1: Create a PostgreSQL database on Render.**
- Go to Render Dashboard > New > PostgreSQL.
- Copy the "External Database URL" or "Internal Database URL" (use Internal if the web service is in the same region).

**Step 2: Create a Redis instance on Render.**
- Go to Render Dashboard > New > Redis.
- Copy the "Internal Redis URL".

**Step 3: Create a Web Service on Render.**
- Connect your GitHub repository.
- Set the Runtime to "Docker" (Render will detect the Dockerfile automatically).
- Set the following environment variables:

| Variable | Value |
|---|---|
| `DATABASE_URL` | The postgres:// URL from Step 1 |
| `REDIS_URL` | The redis:// URL from Step 2 |
| `PORT` | `8080` (Render passes this automatically but setting it explicitly avoids confusion) |

The `RENDER` environment variable is automatically set by Render to `true`. `DatabaseConfig.java` checks for this variable to conditionally append `?sslmode=require` to the JDBC URL (Render's managed PostgreSQL requires SSL; Docker's local PostgreSQL does not).

**Step 4: Deploy.**
- Push to the connected branch (default: `main`). Render builds and deploys automatically.

---

## Configuration Reference

All configuration is in `src/main/resources/application.properties`. Environment variables override the defaults at runtime.

### Database

| Property | Default | Description |
|---|---|---|
| `DATABASE_URL` (env) | not set | postgres:// URL from Render or Docker. If absent, H2 is used. |
| `spring.datasource.url` | `jdbc:h2:mem:simplydone` | H2 fallback for local dev |
| `spring.jpa.hibernate.ddl-auto` | `update` | Auto-creates/updates schema on startup |

### Redis

| Variable | Default | Description |
|---|---|---|
| `REDIS_URL` (env) | `redis://localhost:6380` | Redis connection. Supports `redis://` and `rediss://` (TLS). |

### Scheduler

| Property | Default | Description |
|---|---|---|
| `simplydone.scheduler.polling-interval-ms` | `1000` | How often the scheduler polls Redis (milliseconds) |
| `simplydone.scheduler.queue-prefix` | `simplydone:queue` | Redis key prefix for priority queues |
| `simplydone.scheduler.weights.high` | `70` | DRR weight for HIGH priority queue |
| `simplydone.scheduler.weights.normal` | `20` | DRR weight for NORMAL priority queue |
| `simplydone.scheduler.weights.low` | `10` | DRR weight for LOW priority queue |

The Deficit Round-Robin algorithm distributes processing tokens proportionally. With the default weights, for every 100 tokens: 70 go to HIGH, 20 to NORMAL, and 10 to LOW. Jobs in a lower-priority queue are not starved — they always receive some processing share.

### Rate Limiting

| Property | Default | Description |
|---|---|---|
| `simplydone.rate-limit.requests-per-minute` | `60` | Maximum job submissions per user per window |
| `simplydone.rate-limit.window-seconds` | `60` | Sliding window duration |

Rate limiting is tracked per `userId` in Redis using a sliding-window counter. Exceeding the limit returns HTTP 429.

### Retry

| Property | Default | Description |
|---|---|---|
| `simplydone.retry.max-attempts` | `3` | Total attempts before moving to DLQ |
| `simplydone.retry.initial-delay-seconds` | `5` | Initial retry delay |
| `simplydone.retry.backoff-multiplier` | `2.0` | Delay multiplier per attempt |

Example with defaults: first retry at 5s, second at 10s, third at 20s, then DLQ.

---

## API Reference

All REST endpoints return JSON with the envelope:

```json
{
  "success": true,
  "data": { ... },
  "message": "Human-readable message"
}
```

On error:

```json
{
  "success": false,
  "message": "Error description"
}
```

### Job Endpoints

#### Submit a job

```
POST /api/jobs
Content-Type: application/json

{
  "jobType": "echo",
  "priority": "NORMAL",
  "userId": "user123",
  "payload": {
    "message": "hello world"
  }
}
```

`priority` is optional, defaults to `NORMAL`. Valid values: `HIGH`, `NORMAL`, `LOW`.
`userId` is optional, defaults to `anonymous`.

Response:

```json
{
  "success": true,
  "data": {
    "id": "d4f8a1bc-...",
    "jobType": "echo",
    "status": "QUEUED",
    "priority": "NORMAL",
    "createdAt": "2025-01-15T12:00:00Z"
  }
}
```

#### Get a job by ID

```
GET /api/jobs/{id}
```

#### List jobs (paginated)

```
GET /api/jobs?page=0&size=20
```

Response includes a paginated `content` array of job objects plus pagination metadata.

#### Cancel a job

Jobs can only be cancelled while in `QUEUED` status. A job that has already been picked up by the scheduler cannot be cancelled.

```
DELETE /api/jobs/{id}
```

Returns HTTP 400 if the job is not in `QUEUED` status.

#### List available job types

```
GET /api/jobs/types
```

Returns all registered `JobHandler` implementations with their type name, description, and class name.

#### System health

```
GET /api/jobs/health
```

Returns the count of queued, running, and recent jobs.

---

### Admin Endpoints

#### Get queue statistics

```
GET /api/admin/stats
```

Response:

```json
{
  "success": true,
  "data": {
    "highQueueSize": 3,
    "normalQueueSize": 10,
    "lowQueueSize": 2,
    "totalQueued": 15,
    "totalRunning": 1,
    "totalSuccess": 430,
    "totalFailed": 12,
    "totalDlq": 4,
    "totalProcessed": 446,
    "successRate": 96.4,
    "retryRate": 8.1,
    "throughputPerMinute": 12.5,
    "avgLatencyMs": 245.0
  }
}
```

| Field | Description |
|---|---|
| `highQueueSize` | Jobs currently waiting in the HIGH Redis queue |
| `normalQueueSize` | Jobs currently waiting in the NORMAL Redis queue |
| `lowQueueSize` | Jobs currently waiting in the LOW Redis queue |
| `totalQueued` | Jobs in the database with status QUEUED |
| `totalRunning` | Jobs currently executing |
| `totalSuccess` | All-time successful completions |
| `totalFailed` | All-time failures (not yet in DLQ) |
| `totalDlq` | All-time dead-lettered jobs |
| `successRate` | successRate = totalSuccess / totalProcessed * 100 |
| `retryRate` | Percentage of completed jobs that required at least one retry |
| `throughputPerMinute` | Successful completions in the last 60 seconds |
| `avgLatencyMs` | Mean time from RUNNING to SUCCESS over the last 5 minutes |

#### Clear all queues

Removes all job IDs from the Redis priority queues. The corresponding `JobEntity` rows in PostgreSQL are not deleted. Jobs that were `QUEUED` will remain in the database but will never be processed unless re-submitted.

```
DELETE /api/admin/queues
```

#### List dead letter queue

```
GET /api/admin/dlq
```

#### Retry a DLQ job

Re-submits a dead-lettered job back to the NORMAL queue by creating a fresh `JobEntity` copy with reset attempt count.

```
POST /api/admin/dlq/{id}/retry
```

#### List registered handlers

```
GET /api/admin/handlers
```

---

### SSE Event Stream

The dashboard connects to this endpoint using the browser's `EventSource` API. A single persistent HTTP connection is kept open and the server pushes events as jobs change state.

```
GET /api/events
Accept: text/event-stream
```

The connection ID is a random UUID. The server maintains a `ConcurrentHashMap` of all active connections. Dead connections (browser tab closed, network drop) are pruned automatically when the next broadcast fails with an `IOException`.

**Event types:**

| Event name | Payload fields | Fired when |
|---|---|---|
| `connected` | `connected: true` | Immediately on subscribe |
| `JOB_CREATED` | `id, jobType, status, priority, userId` | Job accepted and queued |
| `JOB_STARTED` | `id, jobType, status, priority` | Executor picks up the job |
| `JOB_COMPLETED` | `id, status, result, durationMs` | Job finishes successfully |
| `JOB_RETRY` | `id, status, attempt, maxRetries, retryInMs` | Job scheduled for retry |
| `JOB_FAILED` | `id, status, result` | Job moved to DLQ |

The browser auto-reconnects with a 5-second delay on connection loss.

**Browser usage example:**

```javascript
const es = new EventSource('/api/events');
es.addEventListener('JOB_COMPLETED', e => {
  const job = JSON.parse(e.data);
  console.log('Completed:', job.id, 'in', job.durationMs, 'ms');
});
```

---

### Keep-Alive Endpoint

```
GET /ping
```

Returns HTTP 200 immediately without any database or Redis interaction. Designed to be called by external uptime monitors or the GitHub Actions keep-alive workflow.

Response:

```json
{
  "pong": true,
  "ts": "2025-01-15T12:00:00.000Z"
}
```

---

## Job Types and Payloads

### echo

Logs and returns the `message` field. Useful for testing and verifying the queue is running.

```json
{
  "jobType": "echo",
  "payload": {
    "message": "hello world"
  }
}
```

Result: `Echo: hello world`

### delay

Sleeps for the specified number of seconds. Simulates a slow or long-running task.

```json
{
  "jobType": "delay",
  "payload": {
    "seconds": 10
  }
}
```

Result: `Delayed 10 seconds`

### http-call

Makes an outbound HTTP request from the server. Useful for invoking webhooks, calling external APIs, or chaining services.

```json
{
  "jobType": "http-call",
  "payload": {
    "url": "https://httpbin.org/post",
    "method": "POST",
    "headers": {
      "Content-Type": "application/json",
      "Authorization": "Bearer your-token"
    },
    "body": "{\"key\": \"value\"}"
  }
}
```

| Field | Required | Description |
|---|---|---|
| `url` | Yes | Full URL including scheme |
| `method` | No | `GET` or `POST`. Defaults to `GET` |
| `headers` | No | JSON object of header name → value pairs |
| `body` | No | Request body string for POST requests |

Result: The HTTP response status code and first 200 characters of the response body.

---

## Writing a Custom Job Handler

Create a Spring-managed bean implementing `JobHandler`. The framework auto-discovers and registers it.

```java
package com.learnerview.simplydone.demo;

import com.learnerview.simplydone.handler.JobContext;
import com.learnerview.simplydone.handler.JobHandler;
import com.learnerview.simplydone.handler.JobResult;
import org.springframework.stereotype.Component;

@Component
public class SendEmailJobHandler implements JobHandler {

    @Override
    public String getJobType() {
        return "send-email";
    }

    @Override
    public String getDescription() {
        return "Sends an email. Payload: to (required), subject, body.";
    }

    @Override
    public JobResult handle(JobContext ctx) {
        String to      = (String) ctx.getPayload().getOrDefault("to", "");
        String subject = (String) ctx.getPayload().getOrDefault("subject", "(no subject)");
        String body    = (String) ctx.getPayload().getOrDefault("body", "");

        if (to.isBlank()) {
            return JobResult.failure("Missing required field: to");
        }

        // ... your email sending logic here

        return JobResult.success("Email sent to " + to);
    }
}
```

The handler is automatically registered in `JobHandlerRegistry` on startup. Verify it appears at `GET /api/jobs/types`.

**Payload example:**

```json
{
  "jobType": "send-email",
  "priority": "HIGH",
  "payload": {
    "to": "user@example.com",
    "subject": "Your report is ready",
    "body": "Click here to download..."
  }
}
```

**Important:**
- Throw an unchecked exception or return `JobResult.failure(...)` to trigger a retry.
- The handler receives `ctx.getAttemptNumber()` (0-indexed) if you need to vary behavior by attempt.
- Keep handlers stateless. Any dependencies should be injected via Spring.

---

## Workflow (DAG) Jobs

A workflow is a set of jobs with declared dependencies forming a Directed Acyclic Graph. SimplyDone resolves execution order via topological sort. Jobs with no dependencies run immediately; downstream jobs are queued as their dependencies complete.

```
POST /api/jobs/workflow
Content-Type: application/json

{
  "userId": "alice",
  "jobs": [
    {
      "id": "fetch-data",
      "jobType": "http-call",
      "payload": { "url": "https://api.example.com/data", "method": "GET" }
    },
    {
      "id": "transform-a",
      "jobType": "echo",
      "payload": { "message": "transforming part A" },
      "dependsOn": ["fetch-data"]
    },
    {
      "id": "transform-b",
      "jobType": "echo",
      "payload": { "message": "transforming part B" },
      "dependsOn": ["fetch-data"]
    },
    {
      "id": "publish",
      "jobType": "http-call",
      "payload": { "url": "https://api.example.com/publish", "method": "POST" },
      "dependsOn": ["transform-a", "transform-b"]
    }
  ]
}
```

The above graph runs `fetch-data` first, then `transform-a` and `transform-b` in parallel (both only waiting on `fetch-data`), then `publish` once both transforms are complete.

The `id` field in the workflow is a client-assigned logical name used only for `dependsOn` references. The actual persisted job ID is a UUID assigned by the server.

A `CyclicDependencyException` is thrown (HTTP 400) if a cycle is detected.

---

## Priority Queuing

Jobs are placed in one of three Redis lists depending on the `priority` field:

```
simplydone:queue:HIGH
simplydone:queue:NORMAL
simplydone:queue:LOW
```

The `SchedulerEngine` uses **Deficit Round-Robin (DRR)** to dequeue based on weights. With the default weights of 70/20/10:

- HIGH queue receives 70 processing tokens out of 100.
- NORMAL queue receives 20.
- LOW queue receives 10.

If a higher-weighted queue is empty, its tokens carry over to the next cycle. Lower-priority jobs are never starved — they always receive a proportional share of the execution bandwidth.

To bias the system for your workload, adjust these properties:

```properties
simplydone.scheduler.weights.high=50
simplydone.scheduler.weights.normal=35
simplydone.scheduler.weights.low=15
```

---

## Retry and Exponential Backoff

When a `JobHandler` throws an exception or returns `JobResult.failure(...)`, `RetryService` evaluates whether a retry is possible.

**Delay formula:**

```
delay = initialDelaySeconds * (backoffMultiplier ^ attemptNumber)
```

With defaults (`initialDelaySeconds=5`, `backoffMultiplier=2.0`, `maxAttempts=3`):

| Attempt | Delay before retry |
|---|---|
| 1st retry (attempt 1) | 5 seconds |
| 2nd retry (attempt 2) | 10 seconds |
| 3rd attempt fails | Moved to DLQ |

The job status is set to `QUEUED` during the delay period. The Redis RPUSH is called with the actual delay implemented via a scheduled re-enqueue.

---

## Dead Letter Queue

After exhausting all retry attempts, the job is moved to the Dead Letter Queue:
- `status` is set to `DLQ`.
- `result` contains the last failure message.
- The job is visible on the DLQ page of the dashboard.

Jobs in the DLQ can be retried manually via `POST /api/admin/dlq/{id}/retry`. This creates a fresh `JobEntity` with `attemptCount` reset to 0 and pushes it to the NORMAL queue.

---

## Rate Limiting

Job submissions are rate-limited per `userId` using a **sliding-window counter** stored in Redis.

Default: 60 submissions per user per 60-second window.

When exceeded:

```
HTTP 429 Too Many Requests
{
  "success": false,
  "message": "Rate limit exceeded for user: alice"
}
```

The counter key in Redis is `simplydone:ratelimit:<userId>` with an expiry matching the window size. To disable rate limiting for internal users, set a very high `requests-per-minute` value for those user IDs.

---

## Real-Time Dashboard

The dashboard at `http://localhost:8080/` shows live metrics and job state using Server-Sent Events.

### Features

**Job submission form (`/jobs`)**
- Select job type and the payload textarea auto-fills with the correct template and a usage hint.
- Section header has a live/pause toggle. When paused, the 10-second polling intervals stop.
- Filter bar above the table lets you filter by status, priority, or search text. Filters apply instantly, client-side.
- Click the ID column to expand the full row showing the complete job ID, user, raw payload, and result.
- Click the ID code itself to copy the full UUID to the clipboard.
- Export CSV button downloads all currently loaded jobs as a timestamped CSV file.

**Admin page (`/admin`)**
- Eight stat cards covering queue depths and execution counts.
- Four enhanced metric cards: Success Rate, Retry Rate, Throughput/min, Avg Latency. Each is color-coded green (healthy), yellow (degraded), or red (critical).

**Dead Letter Queue page (`/dlq`)**
- Table of all dead-lettered jobs with retry button.
- Click job ID to copy.

**Sidebar footer**
- SSE connection indicator: pulsing green dot when connected, yellow when reconnecting, red when offline.
- Keep-alive last ping time showing the most recent browser-to-server ping.

**Toast notifications**
- Every job lifecycle event received via SSE produces a toast notification in the top-right corner.
- Four variants: success (green), warn (yellow), error (red), info (blue).
- Toasts auto-dismiss after 2-6 seconds depending on type. Click to dismiss immediately.

---

## Keep-Alive for Render Free Tier

Render's free tier suspends a web service after 15 minutes of inactivity. The following layers address this:

### Layer 1: Browser ping (while dashboard is open)

The JavaScript in `app.js` calls `GET /ping` every 4 minutes using `setInterval`. The `/ping` endpoint (`PingController`) responds with `{"pong": true}` without touching the database or Redis, so the response is immediate even if the app has just woken up from sleep.

This keeps the service alive as long as at least one browser tab has the dashboard open.

### Layer 2: GitHub Actions scheduled workflow (always-on, no browser required)

This is the recommended solution for keeping the service alive 24/7.

The file `.github/workflows/keep-alive.yml` runs `curl` against your Render URL every 14 minutes using GitHub's hosted runners. This runs entirely outside the JVM, so it works even when the service is sleeping — the ping itself is what wakes it up.

**Setup:**

1. Push this repository to GitHub.
2. Go to your repository on GitHub.
3. Navigate to Settings > Secrets and variables > Actions > New repository secret.
4. Create a secret named `RENDER_APP_URL` with value `https://your-app-name.onrender.com`.
5. The workflow runs automatically every 14 minutes.
6. You can also trigger it manually from the Actions tab using the "Run workflow" button.

The workflow does not count against GitHub Actions billing minutes for public repositories. For private repositories, each run takes approximately 5-10 seconds; billing rounds up to 1 minute per run. At 14-minute intervals (about 3,000 runs/month at roughly 1 minute each), this approaches the 500 free minutes available to private repository owners. If you have a private repository, use cron-job.org instead (see below).

### Layer 3: External cron service (alternative to GitHub Actions)

Any of the following free services can ping `/ping` at a configured interval with no code required:

| Service | Free interval | Setup |
|---|---|---|
| cron-job.org | Every 1 minute | https://cron-job.org → create job → URL: your-app.onrender.com/ping |
| UptimeRobot | Every 5 minutes | https://uptimerobot.com → Add Monitor → HTTP(s) |
| Freshping | Every 1 minute | https://freshping.io |
| Better Uptime | Every 3 minutes | https://betterstack.com |

cron-job.org is recommended for private repositories and requires no GitHub account: register, add a cron job pointing to `https://your-app.onrender.com/ping`, set the interval to every 13 minutes.

### Why internal @Scheduled does not work

Spring's `@Scheduled` annotation runs on the JVM thread pool. When Render suspends a free-tier service, the entire JVM process is frozen. No threads run, no timers fire, no code executes. Only an inbound HTTP request from outside can wake the service. This is why only external callers (browser JS or an external cron) are effective.

---

## Monitoring and Metrics

Spring Boot Actuator is configured with the following endpoints exposed:

```
GET /actuator/health    Application health (UP/DOWN)
GET /actuator/metrics   All Micrometer metrics
GET /actuator/prometheus  Prometheus-format metrics scrape endpoint
```

The Prometheus endpoint can be scraped by a Prometheus server and visualized in Grafana. Useful built-in metrics include:

- `http.server.requests` — request count, latency percentiles by endpoint
- `jvm.memory.used` / `jvm.memory.max` — heap and non-heap usage
- `hikaricp.connections.active` — database connection pool usage
- `redis.commands.duration` — Redis operation latency

Custom metrics can be injected via `MeterRegistry` if you extend the service layer.
