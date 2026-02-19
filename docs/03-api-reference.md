# API Reference

SimplyDone exposes two REST API namespaces: `/api/jobs` for job submission and status, and `/api/admin` for system management. An enhanced v2 API is available at `/api/v2/jobs`.

All request and response bodies use JSON. Timestamps are ISO 8601 strings (`2024-01-01T00:00:00Z`).

---

## Job API — `/api/jobs`

### POST /api/jobs

Submit a job for execution.

**Request body:**

```json
{
  "message": "string (required)",
  "priority": "HIGH | LOW (required)",
  "delay": 0,
  "userId": "string (required)",
  "jobType": "EMAIL_SEND | DATA_PROCESS | API_CALL | FILE_OPERATION | NOTIFICATION | REPORT_GENERATION | CLEANUP (required)",
  "parameters": {}
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| message | string | Yes | Human-readable description of the job |
| priority | string | Yes | `HIGH` or `LOW` |
| delay | integer | No | Seconds to wait before execution (default 0) |
| userId | string | Yes | Identifier of the submitting user |
| jobType | string | Yes | One of the seven supported job types |
| parameters | object | No | Job-type-specific parameters (see [Job Types](04-job-types.md)) |

**Response — 201 Created:**

```json
{
  "id": "job-abc123",
  "status": "SUBMITTED",
  "submittedAt": "2024-01-01T00:00:00Z",
  "executeAt": "2024-01-01T00:00:00Z",
  "message": "Job submitted successfully"
}
```

**Error responses:**

| Status | Cause |
|---|---|
| 400 | Validation failure (missing required field, invalid enum value) |
| 429 | Rate limit exceeded for the user (10 jobs per minute by default) |

---

### GET /api/jobs/{jobId}

Retrieve the current state of a job.

**Path parameter:** `jobId` — the ID returned when the job was submitted.

**Response — 200 OK:**

```json
{
  "success": true,
  "job": {
    "id": "job-abc123",
    "jobType": "API_CALL",
    "status": "EXECUTED",
    "priority": "HIGH",
    "message": "My job",
    "userId": "user-1",
    "submittedAt": "2024-01-01T00:00:00Z",
    "executeAt": "2024-01-01T00:00:00Z"
  }
}
```

**Error responses:**

| Status | Cause |
|---|---|
| 404 | Job not found |

---

### DELETE /api/jobs/{jobId}

Cancel a pending job and remove it from the queue.

**Path parameter:** `jobId`

**Response — 200 OK:**

```json
{
  "success": true,
  "message": "Job cancelled successfully",
  "jobId": "job-abc123"
}
```

---

### GET /api/jobs/rate-limit/{userId}

Get the current rate limit status for a user.

**Path parameter:** `userId`

**Response — 200 OK:**

```json
{
  "userId": "user-1",
  "requestsThisMinute": 3,
  "requestsPerMinute": 10,
  "remaining": 7,
  "resetAt": "2024-01-01T00:01:00Z"
}
```

---

### GET /api/jobs/health

Check whether the Job Scheduler service is responding.

**Response — 200 OK:**

```json
{
  "status": "UP",
  "service": "Job Scheduler",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

---

## Enhanced Job API — `/api/v2/jobs`

The v2 API accepts a richer request body with additional scheduling and resource control options. It is enabled when `simplydone.enhanced-executor=true` (default).

### POST /api/v2/jobs

Submit an enhanced job.

**Request body:**

```json
{
  "jobType": "API_CALL (required)",
  "message": "string (required)",
  "priority": "HIGH | LOW (required)",
  "delay": 0,
  "userId": "string (required)",
  "parameters": {},
  "scheduledAtEpochSeconds": null,
  "timeoutSeconds": null,
  "maxRetries": null,
  "dependencies": []
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| jobType | string | Yes | One of the seven supported job types |
| message | string | Yes | Human-readable description |
| priority | string | Yes | `HIGH` or `LOW` |
| delay | integer | No | Seconds before execution (default 0, ignored if `scheduledAtEpochSeconds` is set) |
| userId | string | Yes | Submitting user identifier |
| parameters | object | No | Job-type-specific parameters |
| scheduledAtEpochSeconds | long | No | Unix epoch timestamp for scheduled execution (overrides `delay`) |
| timeoutSeconds | integer | No | Maximum execution time in seconds (default 300) |
| maxRetries | integer | No | Override the system default retry count |
| dependencies | string[] | No | Job IDs that must complete before this job runs |

**Response — 201 Created:** same shape as `/api/jobs` POST response.

---

### GET /api/v2/jobs/types

List all supported job types with descriptions.

**Response — 200 OK:**

```json
{
  "supportedJobTypes": ["EMAIL_SEND", "DATA_PROCESS", "API_CALL", "FILE_OPERATION", "NOTIFICATION", "REPORT_GENERATION", "CLEANUP"],
  "count": 7,
  "descriptions": {
    "EMAIL_SEND": "Sends an email to specified recipients",
    "DATA_PROCESS": "Processes and transforms data files"
  }
}
```

---

### POST /api/v2/jobs/estimate

Estimate execution time and resource requirements for a job without submitting it.

**Request body:** same as `POST /api/v2/jobs`.

**Response — 200 OK:**

```json
{
  "jobType": "API_CALL",
  "estimatedExecutionTimeSeconds": 30,
  "resourceRequirements": {
    "cpuUnits": 1,
    "memoryMB": 100
  },
  "canExecute": true
}
```

---

### POST /api/v2/jobs/validate

Validate a job request without submitting it.

**Request body:** same as `POST /api/v2/jobs`.

**Response — 200 OK:**

```json
{
  "valid": true,
  "message": "Job is valid and can be executed"
}
```

**Response when invalid:**

```json
{
  "valid": false,
  "message": "Validation failed",
  "error": "missing required parameter: url"
}
```

---

### GET /api/v2/jobs/{jobId}

Same as `GET /api/jobs/{jobId}`.

---

### DELETE /api/v2/jobs/{jobId}

Same as `DELETE /api/jobs/{jobId}`.

---

### GET /api/v2/jobs/rate-limit/{userId}

Same as `GET /api/jobs/rate-limit/{userId}`.

---

### GET /api/v2/jobs/statistics/by-type

Get execution statistics grouped by job type.

**Response — 200 OK:**

```json
{
  "jobTypeStatistics": {
    "API_CALL": {
      "total": 42,
      "completed": 40,
      "failed": 2,
      "avgExecutionTimeMs": 1234
    }
  },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

---

### GET /api/v2/jobs/health

Same as `GET /api/jobs/health` with `"version": "2.0"` in the response.

---

## Admin API — `/api/admin`

Admin endpoints are enabled when `simplydone.scheduler.api.admin-endpoints=true` (default true).

### GET /api/admin/stats

Get system-wide statistics.

**Response — 200 OK:** an object containing total executed count, total rejected count, queue depths, and worker state.

---

### GET /api/admin/queues/high

Get all jobs currently in the HIGH priority queue.

**Response — 200 OK:** array of job objects.

---

### GET /api/admin/queues/low

Get all jobs currently in the LOW priority queue.

**Response — 200 OK:** array of job objects.

---

### DELETE /api/admin/queues/clear

Remove all jobs from both queues.

**Response — 200 OK:**

```json
{
  "success": true,
  "clearedJobs": 15
}
```

---

### DELETE /api/admin/queues/clear/{priority}

Remove all jobs from a specific queue.

**Path parameter:** `priority` — `HIGH` or `LOW`.

**Response — 200 OK:** same shape as clear-all response.

---

### GET /api/admin/dead-letter-queue

Get all jobs in the dead-letter queue (jobs that exhausted all retry attempts).

**Response — 200 OK:**

```json
{
  "deadLetterJobs": [],
  "totalJobs": 0,
  "timestamp": "2024-01-01T00:00:00Z"
}
```

---

### DELETE /api/admin/dead-letter-queue

Clear all jobs from the dead-letter queue.

**Response — 200 OK:**

```json
{
  "success": true,
  "message": "Dead-letter queue cleared",
  "clearedJobs": 3
}
```

---

### POST /api/admin/dead-letter-queue/{jobId}/retry

Re-queue a specific job from the dead-letter queue for another execution attempt.

**Path parameter:** `jobId`

**Response — 200 OK:**

```json
{
  "success": true,
  "message": "Job re-queued for retry",
  "jobId": "job-abc123"
}
```

---

### GET /api/admin/retry-stats

Get retry system statistics.

**Response — 200 OK:** object containing total retry attempts, successes, failures, and per-job-type breakdown.

---

### GET /api/admin/rate-limit/{userId}

Same as `GET /api/jobs/rate-limit/{userId}`.

---

### GET /api/admin/performance

Get performance metrics for all job types.

**Response — 200 OK:** object with per-type metrics including average execution time, min, max, and total count.

---

### GET /api/admin/jobs/user/{userId}

Get all known jobs for a specific user.

**Path parameter:** `userId`

**Response — 200 OK:**

```json
{
  "userId": "user-1",
  "jobs": []
}
```

---

### GET /api/admin/health

Get detailed system health including Redis and PostgreSQL connectivity.

**Response — 200 OK:** health object with component-level status and connection details.

---

## Actuator endpoints

Spring Boot Actuator endpoints are available for monitoring:

| Endpoint | URL |
|---|---|
| Health | `/actuator/health` |
| Info | `/actuator/info` |
| Metrics | `/actuator/metrics` |
| Prometheus | `/actuator/prometheus` |

---

## Error response format

All errors return a JSON object:

```json
{
  "timestamp": "2024-01-01T00:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Job type cannot be null",
  "path": "/api/jobs"
}
```
