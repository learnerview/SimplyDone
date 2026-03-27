# SimplyDone

SimplyDone is a resilient async job execution engine designed for backend integration workloads.

It focuses on one problem only:
- accept jobs safely with idempotent submission
- queue jobs in Redis
- execute jobs from workers against external HTTP endpoints
- retry with backoff and move exhausted jobs to DLQ

## Identity and Scope

SimplyDone is intentionally not a workflow orchestrator and not an app-specific business process engine.

It is a reusable execution substrate for tasks such as:
- webhooks
- payment sync retries
- email or notification dispatch
- external API side effects that must survive failures

## Architecture Summary

The application runs in two profiles from the same codebase.

- api profile:
  - exposes REST endpoints and dashboard views
  - validates requests and enforces idempotency
  - writes canonical state to PostgreSQL
  - enqueues ready jobs into Redis
- worker profile:
  - polls Redis priority queues
  - performs DB CAS claim with lease metadata
  - executes external HTTP calls
  - handles retry scheduling, DLQ moves, and lease recovery

High-level flow:

```text
Client
  -> API profile
       -> PostgreSQL (source of truth)
       -> Redis (ready queues)
  -> Worker profile
       -> Redis dequeue
       -> CAS claim in DB (RUNNING + lease token + visible_at)
       -> External HTTP execution
       -> SUCCESS or RETRY_SCHEDULED or DLQ
```

## Core Reliability Mechanics

1. Idempotency
- Unique key at DB level: UNIQUE(producer, idempotency_key)
- Duplicate submissions return the existing job instead of creating a second one

2. Visibility timeout and lease
- Job is claimed using compare-and-set update
- Worker writes lease_owner, lease_token, visible_at
- Expired leases are detected and recovered by reaper

3. Deterministic retry model
- On failure: attemptCount increases, status becomes RETRY_SCHEDULED, nextRunAt is computed
- Retry promoter moves due RETRY_SCHEDULED jobs back to QUEUED and Redis
- When attempts are exhausted, job moves to DLQ

4. Backpressure and resilience
- API rejects new jobs when queue depth passes configured max depth
- Circuit breaker and bulkhead protect external execution path

## Data Model Notes

Job includes the key operational fields:
- producer
- idempotencyKey
- status
- priority
- attemptCount
- maxAttempts
- nextRunAt
- visibleAt
- leaseOwner
- leaseToken
- executionType
- executionEndpoint
- timeoutSeconds
- callbackUrl

## Job Submission Contract

```json
{
  "jobType": "external",
  "producer": "order-service",
  "idempotencyKey": "order-123",
  "priority": "NORMAL",
  "payload": {
    "orderId": "123",
    "event": "order.confirmed"
  },
  "execution": {
    "type": "HTTP",
    "endpoint": "https://api.myservice.com/process"
  },
  "maxAttempts": 3,
  "timeoutSeconds": 10,
  "callbackUrl": "https://myapp.com/result"
}
```

## Job Lifecycle

```text
QUEUED
  -> RUNNING
       -> SUCCESS
       -> RETRY_SCHEDULED
            -> QUEUED (via retry promoter)
       -> DLQ (if attempts exhausted)
```

## Profiles and Run Commands

Profile selection:

```properties
spring.profiles.active=${SPRING_PROFILES_ACTIVE:api}
```

Run API profile:

```bash
SPRING_PROFILES_ACTIVE=api mvn spring-boot:run
```

Run worker profile:

```bash
SPRING_PROFILES_ACTIVE=worker mvn spring-boot:run
```

## API Endpoints

Jobs:
- POST /api/jobs
- GET /api/jobs
- GET /api/jobs/{id}
- DELETE /api/jobs/{id}

Admin:
- GET /api/admin/stats
- GET /api/admin/queues
- DELETE /api/admin/queues
- GET /api/admin/dlq
- POST /api/admin/dlq/{id}/retry

Streaming:
- GET /api/events

Health ping:
- GET /ping

## Configuration Reference

Only high-signal properties are kept in application.properties.

Runtime and DB:
- spring.profiles.active
- spring.datasource.url
- spring.jpa.hibernate.ddl-auto
- spring.jpa.open-in-view

Scheduler and queue:
- simplydone.scheduler.polling-interval-ms
- simplydone.scheduler.queue-prefix
- simplydone.scheduler.weights.high
- simplydone.scheduler.weights.normal
- simplydone.scheduler.weights.low
- simplydone.queue.max-depth

Retries and lease:
- simplydone.retry.max-attempts
- simplydone.retry.initial-delay-seconds
- simplydone.retry.backoff-multiplier
- simplydone.worker.lease-timeout-seconds
- simplydone.worker.retry-promoter-interval-ms
- simplydone.worker.lease-reaper-interval-ms

Resilience:
- resilience4j.circuitbreaker.instances.externalHttpExecutor.*
- resilience4j.bulkhead.instances.externalHttpExecutor.*

Observability:
- management.endpoints.web.exposure.include
- management.metrics.export.prometheus.enabled

## Testing Focus

The current tests focus on critical reliability paths:
- duplicate submission idempotency behavior
- retry promotion of due jobs
- lease-expiry recovery behavior

Recommended additional tests:
- worker crash during running execution
- Redis restart during processing
- DB restart and recovery behavior
- timeout and circuit-breaker opening scenarios

## Java SDK

A minimal Java client is provided at:

[src/main/java/com/learnerview/simplydone/sdk/SimplyDoneClient.java](src/main/java/com/learnerview/simplydone/sdk/SimplyDoneClient.java)

It can submit external jobs with producer and idempotency metadata using standard HTTP.

## Operational Guidance

For production:
- run at least one API instance and at least two worker instances
- keep PostgreSQL as source of truth and Redis as queue transport
- monitor queue depth, retry rate, DLQ count, and external call failure rate
- set conservative timeoutSeconds and maxAttempts per workload
