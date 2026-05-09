# SimplyDone

## Overview

SimplyDone is a multi-tenant background job scheduling and execution system built with Spring Boot. It accepts jobs over HTTP, persists state in PostgreSQL, uses Redis for queueing and rate limiting, and runs execution in a separate worker process. It is fully open source — host it yourself and integrate into your microservices.

## Core Capabilities

- Priority-based job scheduling with weighted fairness (HIGH / NORMAL / LOW).
- Tenant-scoped APIs and data access via API keys.
- Retry handling with exponential backoff and a dead letter queue (DLQ).
- Organization-level DLQ access — tenants can view and retry their own failed jobs.
- Lease-based execution safety for running jobs.
- Live job status updates through Server-Sent Events (SSE).
- HMAC-SHA256 webhook signatures for verifying outbound request authenticity.
- Interactive API documentation via Swagger UI.
- Standardized error responses (RFC 7807 Problem Details).

## Architecture

The API accepts requests, validates API keys, and stores jobs. Redis holds ready jobs by priority, while PostgreSQL is the source of truth for job state, leases, and execution history. Workers poll Redis, claim ready jobs with an atomic queue operation, and write lease state before executing the job. A reaper process returns orphaned jobs to the queue when a worker disappears.

## Execution Flow

1. A client submits a job through `POST /api/jobs`.
2. The API validates the request, applies rate limits, and persists the job.
3. The job is added to the Redis queue with its priority and scheduled run time.
4. A worker claims the job and writes a lease in PostgreSQL.
5. The worker dispatches an HTTP POST to the client's configured endpoint, attaching an `X-SimplyDone-Signature` HMAC header for webhook verification.
6. Failures are retried with exponential backoff until the retry limit is reached.
7. Jobs that exceed retries are moved to the DLQ for manual review.
8. Jobs that lose their lease are re-queued by the recovery loop.

## Webhook Signature Verification

When SimplyDone executes a job, it computes an HMAC-SHA256 hash of the JSON payload using the organization's API key and sends it in the `X-SimplyDone-Signature` header (prefixed with `sha256=`). Clients should recompute the hash on their end and compare to verify the request originated from SimplyDone.

## API Surface

### Authentication Endpoints (public)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/auth/signup/request-otp` | Send a verification code to the provided email |
| `POST` | `/api/auth/signup/verify-otp` | Verify the code and receive an API key |
| `POST` | `/api/auth/recover/request-otp` | Send a recovery code to a registered email |
| `POST` | `/api/auth/recover/verify-otp` | Verify the code, revoke all old keys, and receive a new one |

### Job Endpoints (authenticated)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/jobs` | Submit a new job |
| `GET` | `/api/jobs` | List jobs for the current tenant |
| `GET` | `/api/jobs/{id}` | Get a single job |
| `DELETE` | `/api/jobs/{id}` | Cancel a job |
| `GET` | `/api/jobs/health` | Queue and execution health |
| `GET` | `/api/jobs/dlq` | List DLQ jobs (scoped to tenant; admins see all) |
| `POST` | `/api/jobs/dlq/{id}/retry` | Re-queue a DLQ job |

### Admin Endpoints (ROLE_ADMIN only)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/admin/stats` | Cluster-wide queue and execution metrics |
| `GET` | `/api/admin/dlq` | List all DLQ jobs (system-wide) |
| `POST` | `/api/admin/dlq/{id}/retry` | Re-queue any DLQ job |
| `GET` | `/api/admin/keys` | List all API keys (values are masked; full key shown only at creation) |
| `POST` | `/api/admin/keys` | Issue a new API key |
| `DELETE` | `/api/admin/keys/{id}` | Revoke an API key |
| `DELETE` | `/api/admin/queues` | Flush all Redis queues |

### Interactive API Docs

Swagger UI is available at `/swagger-ui.html`. Use the `X-API-KEY` header to authenticate.

### Example

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "X-API-KEY: your-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "external",
    "idempotencyKey": "unique-key-123",
    "priority": "NORMAL",
    "execution": { "type": "HTTP", "endpoint": "https://api.yourdomain.com/webhook" },
    "payload": {"type": "email", "to": "user@example.com"},
    "maxAttempts": 3,
    "timeoutSeconds": 30
  }'
```

## Security

- Every API request must include a valid `X-API-KEY` header.
- Admin endpoints additionally require the key to carry the `ADMIN` role.
- Jobs are scoped to the submitting tenant — organizations can only access their own data.
- OTPs are SHA-256 hashed before storage. A database breach does not expose usable codes.
- Admin key listings return masked values (e.g. `sd_sk_****ab3f`). The full key is shown once at creation time only.
- Actuator is restricted to `/actuator/health` and `/actuator/metrics`. Sensitive endpoints cannot be accidentally exposed.
- If you lose your API key, visit `/recover`. Provide your registered email, verify the OTP, and a new key is issued. All previous keys for the account are revoked immediately.

## Reliability Model

- Leases prevent duplicate execution across workers.
- Retries use exponential backoff to avoid retry storms.
- Idempotency keys prevent duplicate job creation.
- Orphan recovery re-queues jobs left in `RUNNING` after a crash.
- DLQ handling keeps terminal failures visible for manual replay.
- CircuitBreaker and Bulkhead (Resilience4j) protect against cascade failures during HTTP execution.

## Running Locally

1. Copy `.env.example` to `.env` if you want to override defaults. Set `ADMIN_INITIAL_SECRET` to bootstrap the admin API key.
2. Start PostgreSQL, Redis, and the app:

   ```bash
   docker compose up -d
   ```

3. Visit `http://localhost:8080` for the public landing page, or `http://localhost:8080/login` to authenticate.

4. Run the application directly if you are not using Docker:

   ```bash
   mvn spring-boot:run
   ```

   Actuator exposes `/actuator/health` and `/actuator/metrics`.

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
