# REST API Reference

The SimplyDone API is organized around REST and provides endpoints for job lifecycle management and system administration.

## API Authentication & Security
- **Namespace**: `/api/jobs` for standard operations, `/api/admin` for administrative tasks.
- **Data Format**: All request and response bodies use JSON.
- **Rate Limiting**: Standard tier users are limited to 10 submissions per minute (configurable).

## Job Management Endpoints

### Submit Job
`POST /api/jobs`

Initiates a new background task.

**Request Payload:**
```json
{
  "jobType": "EMAIL_SEND | DATA_PROCESS | API_CALL | FILE_OPERATION | ...",
  "message": "Human-readable description",
  "priority": "HIGH | LOW",
  "userId": "identifier",
  "parameters": { ... }
}
```

**Common Parameters:**
- `userId`: Identifier for auditing and rate-limiting.
- `delay`: Seconds to wait before the worker picks up the job.

### Retrieve Job Status
`GET /api/jobs/{jobId}`

Returns the current execution state and metadata for a specific job.

### Cancel Job
`DELETE /api/jobs/{jobId}`

Removes a pending job from the execution queue.

## Administrative Endpoints

### System Statistics
`GET /api/admin/stats`

Provides an aggregated view of system-wide throughput, failure rates, and queue depths.

### Global Queue Inspection
`GET /api/admin/queues/{priority}`

Lists all jobs currently awaiting execution in the specified priority lane.

### Dead Letter Queue (DLQ) Management
- **List DLQ**: `GET /api/admin/dead-letter-queue`
- **Retry Job**: `POST /api/admin/dead-letter-queue/{jobId}/retry`
- **Clear DLQ**: `DELETE /api/admin/dead-letter-queue`

## Error Response Model
In the event of an error, the API returns a standardized JSON object:

```json
{
  "timestamp": "ISO-8601 String",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error context",
  "path": "/api/..."
}
```

## Actuator Monitoring
For dev-ops monitoring, the following Spring Actuator endpoints are exposed:
- `/actuator/health`: System-wide health status.
- `/actuator/metrics`: JVM and application-level performance data.
- `/actuator/prometheus`: Metrics in Prometheus-compatible format.
