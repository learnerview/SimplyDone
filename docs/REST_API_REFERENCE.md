# REST API Reference

All SimplyDone endpoints return a consistent `ApiResponse<T>` envelope. Every field described below is always present in the response unless marked optional.

---

## Response Envelope

```json
{
  "status": 201,
  "success": true,
  "message": "Job submitted successfully and queued for execution",
  "data": { ... },
  "path": "/api/jobs",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

Error responses include additional fields:

```json
{
  "status": 400,
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "Validation failed",
  "validationErrors": {
    "userId": "User ID cannot be blank",
    "message": "Job message cannot be blank"
  },
  "path": "/api/jobs",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Rate Limiting

All job submission endpoints are protected by a per-user, fixed-window rate limiter backed by Redis. The default limit is 60 requests per minute per `userId`. This is configurable via `simplydone.scheduler.rate-limit.requests-per-minute` in `application.properties`.

When the limit is exceeded, the API returns HTTP 429:

```json
{
  "status": 429,
  "success": false,
  "errorCode": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded for user: user-123",
  "path": "/api/jobs",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## Job Management Endpoints

### POST /api/jobs — Submit a Job

Validates the request, assigns a UUID, records the job in PostgreSQL, and adds it to the Redis priority queue.

**Request body:**

```json
{
  "userId": "user-123",
  "jobType": "EMAIL_SEND",
  "priority": "HIGH",
  "message": "Send onboarding email",
  "delay": 0,
  "parameters": {
    "to": "customer@example.com",
    "subject": "Welcome",
    "body": "<h1>Welcome!</h1>"
  }
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `userId` | string | yes | Identifier used for rate limiting and audit |
| `jobType` | string | yes | One of: `EMAIL_SEND`, `DATA_PROCESS`, `API_CALL`, `FILE_OPERATION`, `NOTIFICATION`, `REPORT_GENERATION`, `CLEANUP` |
| `priority` | string | no | `HIGH` or `LOW`. Defaults to `LOW` when omitted |
| `message` | string | yes | Human-readable description of the job |
| `delay` | integer | no | Seconds to wait before the worker picks up the job. `0` means run immediately. Default: `0` |
| `parameters` | object | depends | Job-type-specific parameters. See the Job Type Catalog |

**Response (HTTP 201):**

```json
{
  "status": 201,
  "success": true,
  "message": "Job submitted successfully and queued for execution",
  "data": {
    "id": "7e7e736b-7bce-4c97-beba-2ab1cc0ac5da",
    "message": "Send onboarding email",
    "priority": "HIGH",
    "delaySeconds": 0,
    "submittedAt": "2024-01-15T10:30:00Z",
    "executeAt": "2024-01-15T10:30:00Z",
    "status": "Job submitted successfully"
  },
  "path": "/api/jobs",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

### GET /api/jobs/{jobId} — Retrieve Job Status

Returns the current state and all recorded metadata for a single job.

**Response (HTTP 200):**

```json
{
  "status": 200,
  "success": true,
  "message": "Job retrieved successfully",
  "data": {
    "id": "7e7e736b-7bce-4c97-beba-2ab1cc0ac5da",
    "message": "Send onboarding email",
    "jobType": "EMAIL_SEND",
    "priority": "HIGH",
    "delaySeconds": 0,
    "submittedAt": "2024-01-15T10:30:00Z",
    "executeAt": "2024-01-15T10:30:00Z",
    "executedAt": "2024-01-15T10:30:01Z",
    "status": "EXECUTED",
    "userId": "user-123",
    "attemptCount": 1,
    "maxRetries": 3,
    "parameters": { "to": "customer@example.com", "subject": "Welcome" },
    "executionResult": null,
    "errorMessage": null
  }
}
```

Possible `status` values: `PENDING`, `EXECUTED`, `FAILED`, `CANCELLED`.

---

### DELETE /api/jobs/{jobId} — Cancel a Job

Removes a pending job from the queue. Jobs that are already `EXECUTED` or `FAILED` cannot be cancelled.

**Response (HTTP 200):**

```json
{
  "status": 200,
  "success": true,
  "message": "Job cancelled successfully"
}
```

---

### GET /api/jobs/health — Health Check

Returns queue depth and a health status string. Used by Render as the liveness probe.

**Response (HTTP 200):**

```json
{
  "status": 200,
  "success": true,
  "message": "Job service is operational and ready to accept jobs",
  "data": {
    "queued": 3,
    "highPriority": 1,
    "lowPriority": 2
  }
}
```

---

### GET /api/jobs/rate-limit/{userId} — Rate Limit Status

Returns the current request count and remaining window time for a user.

**Response (HTTP 200):**

```json
{
  "status": 200,
  "success": true,
  "message": "Rate limit status retrieved",
  "data": {
    "currentCount": 5,
    "maxRequests": 60,
    "resetTimeSeconds": 42,
    "allowed": true
  }
}
```

---

## Administrative Endpoints

All admin endpoints are under `/api/admin`.

### GET /api/admin/stats — System Statistics

```json
{
  "data": {
    "executedJobs": 1247,
    "rejectedJobs": 3,
    "highQueueSize": 2,
    "lowQueueSize": 8,
    "totalQueueSize": 10,
    "totalProcessed": 1250,
    "timestamp": "2024-01-15T10:30:00Z"
  }
}
```

---

### GET /api/admin/health — Detailed Health

Returns Redis availability, queue sizes, and a composite health status.

```json
{
  "data": {
    "status": "OK",
    "queues": true,
    "highQueueSize": 2,
    "lowQueueSize": 8,
    "executedJobs": 1247
  }
}
```

---

### GET /api/admin/queues/{priority} — Queue Contents

`priority` must be `high` or `low`.

Returns an array of job objects currently sitting in the queue, waiting to be picked up by the worker.

---

### GET /api/admin/jobs/user/{userId} — Jobs by User

Returns a summary of all pending jobs for a specific user, grouped by priority.

```json
{
  "data": {
    "highPriorityJobs": [ ... ],
    "lowPriorityJobs": [ ... ]
  }
}
```

---

### GET /api/admin/jobs/executed — Completed Job History

Returns all jobs that have reached a terminal state (`EXECUTED` or `FAILED`), sourced from PostgreSQL. Useful for auditing, debugging, and building dashboards.

```json
{
  "status": 200,
  "success": true,
  "message": "Completed jobs retrieved (42 total)",
  "data": [
    {
      "id": "3f8a2b1c-...",
      "jobType": "EMAIL_SEND",
      "userId": "user-42",
      "status": "EXECUTED",
      "submittedAt": "2024-01-15T10:30:00Z",
      "executedAt": "2024-01-15T10:30:01Z",
      "errorMessage": null
    },
    {
      "id": "9e1d4a7f-...",
      "jobType": "API_CALL",
      "userId": "user-10",
      "status": "FAILED",
      "submittedAt": "2024-01-15T10:31:00Z",
      "executedAt": "2024-01-15T10:31:02Z",
      "errorMessage": "Connection refused: external-api.example.com:443"
    }
  ]
}
```

Returns an empty array (not an error) when no jobs have completed yet or when PostgreSQL is unavailable.

---

### GET /api/admin/performance — JVM Metrics

Returns JVM memory usage, thread counts, and uptime information useful for capacity planning.

---

### GET /api/admin/dead-letter-queue — List DLQ

Returns jobs that have exhausted their retry budget. Each entry includes the original job payload plus the final error message.

---

### POST /api/admin/dead-letter-queue/{jobId}/retry — Retry a DLQ Job

Re-enqueues a dead-letter job into the LOW priority queue with its retry counter reset.

---

### DELETE /api/admin/dead-letter-queue — Clear DLQ

Permanently removes all entries from the dead-letter queue.

---

### DELETE /api/admin/queues/clear — Clear All Queues

Removes all pending jobs from both the HIGH and LOW priority queues. Use with caution; this cannot be undone.

---

### GET /api/admin/rate-limit/{userId} — Admin Rate Limit View

Same as `GET /api/jobs/rate-limit/{userId}` but under the admin namespace.

---

## Enhanced Job Submission (Advanced)

`POST /api/jobs/enhanced`

Accepts the same fields as the standard submission plus:

| Field | Type | Description |
|---|---|---|
| `scheduledAtEpochSeconds` | long | Unix timestamp for absolute scheduling (overrides `delay`) |
| `timeoutSeconds` | integer | Maximum execution time before the job is considered failed |
| `maxRetries` | integer | Override the global retry count for this job |
| `dependencies` | string[] | List of job IDs that must be `EXECUTED` before this job runs |
| `batchId` | string | Group ID for batch operations |

---

## Actuator Monitoring Endpoints

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Spring-native health aggregation |
| `GET /actuator/metrics` | JVM and HTTP server metrics |
| `GET /actuator/prometheus` | Prometheus-compatible metrics scrape endpoint |

