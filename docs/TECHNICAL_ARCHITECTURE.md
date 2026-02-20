# Technical Architecture

This document describes the internal components of SimplyDone, how they interact, and the design decisions behind them.

---

## Component Overview

```
                        HTTP / Browser
                              |
                   ViewController (Thymeleaf)
                              |
                   JobController  AdminController  EnhancedJobController
                              |
                   RateLimitingService  (per-user fixed-window counter in Redis)
                              |
                   JobService  (validation, UUID assignment, persistence)
                       |                        |
               JobRepository                JobRepository
              (Redis queues)           (PostgreSQL job status)
                       |
               Background Worker (polls every 1 second)
                       |
               JobExecutorFactory (Strategy pattern lookup by JobType)
                       |
         --------------------------------------------------
         |          |           |          |          |
   EmailJobStrategy  ApiCallJobStrategy  FileOpStrategy  ...
```

---

## Layer Descriptions

### Ingestion Layer

**REST Controllers** — Receive JSON requests, enforce `@Valid` bean validation, delegate to services, and return `ApiResponse<T>` envelopes. There are three controller groups:
- `JobController` at `/api/jobs` — standard job lifecycle
- `AdminController` at `/api/admin` — statistics, queue inspection, DLQ management
- `EnhancedJobController` at `/api/jobs/enhanced` — advanced scheduling options

**ViewController** — Serves Thymeleaf HTML views. Delegates data loading to JavaScript that calls the REST API. No server-side data fetching in templates.

### Rate Limiting

`RateLimitingServiceImpl` implements a **fixed-window counter** per user per minute. For each incoming request:
1. Redis `INCR` on a key of the form `rate_limit:{userId}:{currentMinute}` where `currentMinute` is `epochSeconds / 60`.
2. If the returned counter is `1` (first request in the window), a 60-second TTL is set on the key so it auto-expires.
3. If the counter exceeds `requestsPerMinute`, the request is denied with HTTP 429.
4. If Redis is unavailable, requests are allowed through (fail-open) with a warning log.

### Orchestration Layer

**JobService** — Accepts a `JobSubmissionRequest`, constructs a `Job` domain object (assigns a UUID, computes `executeAt = now + delay`), persists it to both Redis and PostgreSQL, and returns a `JobSubmissionResponse`.

**JobRepository** — Dual-write repository:
- **Redis sorted set**: Job JSON is stored with `executeAt.toEpochMilli()` as the score. This enables the worker to find "ready" jobs with a range query on score `[0, now]`.
- **Redis hash** (status map): A secondary hash maps `jobId -> jobJson` for O(1) status lookups.
- **PostgreSQL** (via `JobEntity`): Persists the canonical job record for durable history.

### Execution Engine

**JobWorker** — A `@Scheduled` method that fires every `intervalMs` (default 1000 ms). On each tick:
1. Queries the HIGH priority sorted set for entries with score `<= currentTimeMillis`.
2. If no HIGH job is ready, queries the LOW priority set.
3. If a job is found, it is atomically removed using a `WATCH + MULTI/EXEC` block.
4. `JobService.executeNextReadyJob()` is called, which in turn calls `JobExecutorFactory`.

**JobExecutorFactory** — Auto-discovers all Spring `@Component` beans implementing `JobExecutionStrategy` and maps them by their `getSupportedJobType()` return value. Adding a new job type requires only creating a new strategy class.

**RetryService** — On execution failure, exponential backoff is computed (`initialDelay * backoffMultiplier^attempt`). The job is re-enqueued with the new `executeAt`. After `maxRetries` attempts, the job is moved to the dead-letter queue (DLQ).

### Storage Layer

**PostgreSQL** — Stores the canonical job record including final status, execution timestamp, error message, and parameters. Used for audit queries (e.g., retrieving all jobs for a user).

**Redis** — Ephemeral working state:
- `jobs:high` — HIGH priority sorted set (score = executeAt millis)
- `jobs:low` — LOW priority sorted set
- `dead_letter:jobs` — DLQ sorted set (score = failure timestamp)
- `simplydone:jobs:status` — Hash map of `jobId -> jobJson` for status lookups
- `stats:executed`, `stats:rejected` — Atomic integer counters
- `rate_limit:{userId}:{minute}` — Per-user rate limit counters with 60-second TTL

---

## Design Patterns

**Strategy Pattern** — Job execution logic is encapsulated in individual strategy classes. The factory resolves the correct strategy at runtime. This makes adding a new job type a single-file change with no modification to the execution engine.

**Repository Pattern** — `JobRepository` abstracts all Redis and PostgreSQL access. Services never interact with storage drivers directly.

**Fail-Open Rate Limiting** — Redis connectivity failures degrade gracefully: the system logs a warning and allows the request rather than becoming unavailable.

**Atomic Queue Operations** — Redis `WATCH + MULTI/EXEC` is used when dequeuing jobs to prevent the same job from being picked up by two worker instances in a horizontally-scaled deployment.

---

## Data Flow: Job Submission

```
1. POST /api/jobs  (client)
2. JobController validates JSON and checks rate limit (Redis INCR)
3. JobService.submitJob():
   a. Assigns UUID
   b. Sets executeAt = now + delaySeconds
   c. Writes job JSON to Redis sorted set (score = executeAt millis)
   d. Writes job JSON to Redis status hash
   e. Writes JobEntity to PostgreSQL
4. Returns 201 with the job ID and executeAt timestamp
```

## Data Flow: Job Execution

```
1. JobWorker ticks (every 1 second)
2. JobRepository.getNextReadyJob(HIGH) queries sorted set [0, now]
3. WATCH + MULTI/EXEC atomically removes the job from the set
4. JobService.executeNextReadyJob() is called:
   a. JobExecutorFactory resolves the strategy by jobType
   b. Strategy.execute(job) runs the business logic
   c. On success: status updated to EXECUTED in Redis hash and PostgreSQL
   d. On failure: RetryService schedules retry or moves to DLQ
5. Executed and rejected counters incremented atomically in Redis
```

