# SimplyDone

## Overview

SimplyDone is a multi-tenant background job scheduling and execution system built with Spring Boot. It accepts jobs over HTTP, persists state in PostgreSQL, uses Redis for queueing and rate limiting, and runs execution in a separate worker process.

## Core Capabilities

- Priority-based job scheduling with weighted fairness.
- Tenant-scoped APIs and data access.
- Retry handling with exponential backoff and a dead letter queue.
- Lease-based execution safety for running jobs.
- Live job status updates through Server-Sent Events.
- Execution tracking for operational review.

## Architecture

The API accepts requests, validates API keys, and stores jobs. Redis holds ready jobs by priority, while PostgreSQL is the source of truth for job state, leases, and execution history. Workers poll Redis, claim ready jobs with an atomic queue operation, and write lease state before executing the job. A reaper process returns orphaned jobs to the queue when a worker disappears.

## Execution Flow

1. A client submits a job through `POST /api/jobs`.
2. The API validates the request, applies rate limits, and persists the job.
3. The job is added to the Redis queue with its priority and scheduled run time.
4. A worker claims the job and writes a lease in PostgreSQL.
5. The worker executes the job and records the outcome.
6. Failures are retried with backoff until the retry limit is reached.
7. Jobs that lose their lease are re-queued by the recovery loop.

## Reliability Model

- Leases prevent duplicate execution across workers.
- Retries use exponential backoff to avoid retry storms.
- Idempotency keys prevent duplicate job creation.
- Orphan recovery re-queues jobs left in `RUNNING` after a crash.
- DLQ handling keeps terminal failures visible for manual replay.

## API Surface

- `POST /api/jobs` submits a job.
- `GET /api/jobs` lists jobs for the current tenant.
- `GET /api/jobs/{id}` returns a single job.
- `GET /api/jobs/health` reports scheduler and queue health.
- `GET /api/admin/stats` returns queue and execution metrics.
- `GET /api/admin/dlq` lists dead-letter jobs.
- `POST /api/admin/dlq/{id}/retry` re-queues a DLQ job.

Example:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "X-API-KEY: demo-key" \
  -H "Content-Type: application/json" \
  -d '{
    "payload": {"type": "email"},
    "nextRunAt": "2026-05-10T10:00:00Z"
  }'
```

## Running Locally

1. Copy `.env.example` to `.env` if you want to override defaults.
2. Start PostgreSQL, Redis, and the app.

   ```bash
   docker compose up -d
   ```

3. Run the application directly if you are not using Docker.

   ```bash
   mvn spring-boot:run
   ```

   Actuator exposes `/actuator/health` and `/actuator/metrics`.

Demo assets live in `scripts/demo-data.sh` and `simplydone.postman_collection.json`.

## Repository Structure

```text
simplydone/
├── src/
├── scripts/
├── Dockerfile
├── docker-compose.yml
├── simplydone.postman_collection.json
├── .env.example
└── README.md
```

