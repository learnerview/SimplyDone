# SimplyDone - API Documentation

## 📋 Overview

SimplyDone provides a comprehensive REST API for priority-based job scheduling with retry logic, rate limiting, and production monitoring.

**Base URL**: `http://localhost:8080/api`

**Authentication**: Currently no authentication required (admin endpoints should be secured in production)

**Content-Type**: `application/json`

---

## 🚀 Job Management API

### Submit New Job

**Endpoint**: `POST /api/jobs`

**Description**: Submit a new job to the priority queue system.

**Request Body**:
```json
{
  "message": "Process payment order #12345",
  "priority": "HIGH",
  "delay": 5,
  "userId": "user123"
}
```

**Request Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `message` | String | Yes | Job description/message |
| `priority` | String | Yes | Job priority: `HIGH` or `LOW` |
| `delay` | Integer | Yes | Delay in seconds before execution |
| `userId` | String | Yes | User identifier for rate limiting |

**Response**:
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Process payment order #12345",
  "priority": "HIGH",
  "delaySeconds": 5,
  "userId": "user123",
  "submittedAt": "2024-01-15T10:30:00.123Z",
  "executeAt": "2024-01-15T10:30:05.123Z",
  "status": "Job submitted successfully"
}
```

**Status Codes**:
- `201 Created`: Job submitted successfully
- `400 Bad Request`: Invalid request parameters
- `429 Too Many Requests`: Rate limit exceeded

**Example**:
```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Process payment",
    "priority": "HIGH",
    "delay": 5,
    "userId": "testuser"
  }'
```

---

### Get Job Details

**Endpoint**: `GET /api/jobs/{jobId}`

**Description**: Retrieve details of a specific job.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `jobId` | String | Unique job identifier |

**Response**:
```json
{
  "success": true,
  "job": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "message": "Process payment order #12345",
    "priority": "HIGH",
    "delaySeconds": 5,
    "userId": "user123",
    "executeAt": "2024-01-15T10:30:05.123Z",
    "submittedAt": "2024-01-15T10:30:00.123Z"
  }
}
```

**Status Codes**:
- `200 OK`: Job found and returned
- `404 Not Found`: Job not found

**Example**:
```bash
curl http://localhost:8080/api/jobs/550e8400-e29b-41d4-a716-446655440000
```

---

### Cancel Job

**Endpoint**: `DELETE /api/jobs/{jobId}`

**Description**: Cancel a pending job.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `jobId` | String | Unique job identifier |

**Response**:
```json
{
  "success": true,
  "message": "Job cancelled successfully",
  "jobId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Status Codes**:
- `200 OK`: Job cancelled successfully
- `404 Not Found`: Job not found or already executed

**Example**:
```bash
curl -X DELETE http://localhost:8080/api/jobs/550e8400-e29b-41d4-a716-446655440000
```

---

### Get Rate Limit Status

**Endpoint**: `GET /api/jobs/rate-limit/{userId}`

**Description**: Check rate limiting status for a user.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `userId` | String | User identifier |

**Response**:
```json
{
  "currentCount": 3,
  "maxRequests": 10,
  "allowed": true,
  "remainingRequests": 7,
  "resetTimeSeconds": 45,
  "userId": "user123"
}
```

**Status Codes**:
- `200 OK`: Rate limit status retrieved

**Example**:
```bash
curl http://localhost:8080/api/jobs/rate-limit/user123
```

---

### Job Service Health

**Endpoint**: `GET /api/jobs/health`

**Description**: Health check for job service.

**Response**:
```json
{
  "status": "UP",
  "service": "Job Scheduler",
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

**Status Codes**:
- `200 OK`: Service is healthy

**Example**:
```bash
curl http://localhost:8080/api/jobs/health
```

---

## 🛠️ Admin Management API

### System Statistics

**Endpoint**: `GET /api/admin/stats`

**Description**: Get comprehensive system statistics.

**Response**:
```json
{
  "highQueueSize": 15,
  "lowQueueSize": 32,
  "totalQueueSize": 47,
  "executedJobs": 1250,
  "rejectedJobs": 18,
  "totalProcessed": 1268,
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

**Example**:
```bash
curl http://localhost:8080/api/admin/stats
```

---

### Inspect High Priority Queue

**Endpoint**: `GET /api/admin/queues/high`

**Description**: Get all jobs in high priority queue.

**Response**:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "message": "Urgent payment processing",
    "priority": "HIGH",
    "delaySeconds": 2,
    "userId": "user456",
    "executeAt": "2024-01-15T10:30:02.123Z"
  }
]
```

**Example**:
```bash
curl http://localhost:8080/api/admin/queues/high
```

---

### Inspect Low Priority Queue

**Endpoint**: `GET /api/admin/queues/low`

**Description**: Get all jobs in low priority queue.

**Response**:
```json
[
  {
    "id": "660e8400-e29b-41d4-a716-446655440001",
    "message": "Generate daily report",
    "priority": "LOW",
    "delaySeconds": 30,
    "userId": "user789",
    "executeAt": "2024-01-15T10:30:30.123Z"
  }
]
```

**Example**:
```bash
curl http://localhost:8080/api/admin/queues/low
```

---

### System Health Check

**Endpoint**: `GET /api/admin/health`

**Description**: Comprehensive system health check.

**Response**:
```json
{
  "status": "UP",
  "service": "SimplyDone Job Scheduler",
  "version": "1.0.0",
  "timestamp": "2024-01-15T10:30:00.123Z",
  "jvm": {
    "maxMemory": 2147483648,
    "totalMemory": 536870912,
    "freeMemory": 234567890,
    "usedMemory": 302303022
  },
  "queues": {
    "highPrioritySize": 15,
    "lowPrioritySize": 32,
    "totalSize": 47
  }
}
```

**Example**:
```bash
curl http://localhost:8080/api/admin/health
```

---

### Retry Statistics

**Endpoint**: `GET /api/admin/retry-stats`

**Description**: Get retry and dead letter queue statistics.

**Response**:
```json
{
  "retryingJobs": 3,
  "totalAttempts": 7,
  "maxAttempts": 3
}
```

**Example**:
```bash
curl http://localhost:8080/api/admin/retry-stats
```

---

### Performance Metrics

**Endpoint**: `GET /api/admin/performance`

**Description**: Get detailed performance metrics.

**Response**:
```json
{
  "jvm": {
    "maxMemory": 2147483648,
    "totalMemory": 536870912,
    "freeMemory": 234567890,
    "usedMemory": 302303022,
    "availableProcessors": 8
  },
  "jobProcessing": {
    "highPriorityQueueSize": 15,
    "lowPriorityQueueSize": 32,
    "totalQueueSize": 47,
    "executedJobs": 1250,
    "rejectedJobs": 18,
    "totalProcessed": 1268,
    "successRate": 98.58
  },
  "retry": {
    "retryingJobs": 3,
    "totalRetryAttempts": 7,
    "maxRetryAttempts": 3
  },
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

**Example**:
```bash
curl http://localhost:8080/api/admin/performance
```

---

### Get User Jobs

**Endpoint**: `GET /api/admin/jobs/user/{userId}`

**Description**: Get all jobs for a specific user.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `userId` | String | User identifier |

**Response**:
```json
{
  "userId": "user123",
  "highPriorityJobs": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "message": "Urgent payment",
      "priority": "HIGH",
      "delaySeconds": 2,
      "userId": "user123"
    }
  ],
  "lowPriorityJobs": [
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "message": "Generate report",
      "priority": "LOW",
      "delaySeconds": 30,
      "userId": "user123"
    }
  ],
  "totalJobs": 2,
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

**Example**:
```bash
curl http://localhost:8080/api/admin/jobs/user/user123
```

---

### Get User Rate Limit Status

**Endpoint**: `GET /api/admin/rate-limit/{userId}`

**Description**: Get rate limiting status for a user (admin view).

**Response**:
```json
{
  "currentCount": 3,
  "maxRequests": 10,
  "allowed": true,
  "remainingRequests": 7,
  "resetTimeSeconds": 45,
  "userId": "user123"
}
```

**Example**:
```bash
curl http://localhost:8080/api/admin/rate-limit/user123
```

---

### Clear All Queues

**Endpoint**: `DELETE /api/admin/queues/clear`

**Description**: Clear all jobs from both high and low priority queues.

**Response**:
```json
{
  "success": true,
  "message": "Queue clear operation completed",
  "clearedJobs": {
    "highPriority": 15,
    "lowPriority": 32,
    "total": 47
  }
}
```

**Example**:
```bash
curl -X DELETE http://localhost:8080/api/admin/queues/clear
```

---

### Clear Specific Queue

**Endpoint**: `DELETE /api/admin/queues/clear/{priority}`

**Description**: Clear jobs from a specific priority queue.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `priority` | String | Queue priority: `HIGH` or `LOW` |

**Response**:
```json
{
  "success": true,
  "message": "HIGH priority queue cleared",
  "clearedJobs": 15
}
```

**Status Codes**:
- `200 OK`: Queue cleared successfully
- `400 Bad Request`: Invalid priority specified

**Example**:
```bash
curl -X DELETE http://localhost:8080/api/admin/queues/clear/HIGH
```

---

### Get Dead Letter Queue

**Endpoint**: `GET /api/admin/dead-letter-queue`

**Description**: Get all jobs in the dead letter queue.

**Response**:
```json
{
  "deadLetterJobs": [
    {
      "id": "dlq-123",
      "originalJob": {
        "id": "job-123",
        "message": "Failed payment processing",
        "priority": "HIGH",
        "userId": "user123"
      },
      "failureReason": "Connection timeout to payment gateway",
      "failureTimestamp": "2024-01-15T10:25:00.123Z",
      "retryAttempts": 3,
      "originalPriority": "HIGH",
      "originalUserId": "user123",
      "canBeRetried": true,
      "retryCount": 1
    }
  ],
  "totalJobs": 1,
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

**Example**:
```bash
curl http://localhost:8080/api/admin/dead-letter-queue
```

---

### Clear Dead Letter Queue

**Endpoint**: `DELETE /api/admin/dead-letter-queue`

**Description**: Clear all jobs from the dead letter queue.

**Response**:
```json
{
  "success": true,
  "message": "Dead letter queue cleared",
  "clearedJobs": 5
}
```

**Example**:
```bash
curl -X DELETE http://localhost:8080/api/admin/dead-letter-queue
```

---

### Retry Dead Letter Job

**Endpoint**: `POST /api/admin/dead-letter-queue/{jobId}/retry`

**Description**: Retry a job from the dead letter queue.

**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `jobId` | String | Original job identifier |

**Response**:
```json
{
  "success": true,
  "message": "Job successfully retried from dead letter queue",
  "jobId": "job-123"
}
```

**Status Codes**:
- `200 OK`: Job retried successfully
- `404 Not Found`: Job not found in dead letter queue

**Example**:
```bash
curl -X POST http://localhost:8080/api/admin/dead-letter-queue/job-123/retry \
  -H "Content-Type: application/json"
```

---

## 📊 Production Monitoring API

### Application Health

**Endpoint**: `GET /actuator/health`

**Description**: Spring Boot Actuator health check.

**Response**:
```json
{
  "status": "UP",
  "components": {
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.0"
      }
    },
    "jobScheduler": {
      "status": "UP",
      "details": {
        "queueSize": 47,
        "processedJobs": 1268
      }
    }
  }
}
```

**Example**:
```bash
curl http://localhost:8080/actuator/health
```

---

### Application Metrics

**Endpoint**: `GET /actuator/metrics`

**Description**: Get available metrics list.

**Response**:
```json
{
  "names": [
    "job.execution.time",
    "job.submission.time",
    "jvm.memory.used",
    "jvm.threads.live",
    "http.server.requests"
  ]
}
```

**Example**:
```bash
curl http://localhost:8080/actuator/metrics
```

---

### Specific Metric

**Endpoint**: `GET /actuator/metrics/{metric-name}`

**Description**: Get details for a specific metric.

**Example**:
```bash
curl http://localhost:8080/actuator/metrics/job.execution.time
```

**Response**:
```json
{
  "name": "job.execution.time",
  "description": "Job execution time",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1250
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 2450.5
    },
    {
      "statistic": "MAX",
      "value": 15.2
    }
  ]
}
```

---

### Prometheus Metrics

**Endpoint**: `GET /actuator/prometheus`

**Description**: Get metrics in Prometheus format.

**Response**:
```
# HELP job_execution_time_seconds Job execution time
# TYPE job_execution_time_seconds summary
job_execution_time_seconds_count 1250.0
job_execution_time_seconds_sum 2450.5
job_execution_time_seconds_max 15.2

# HELP job_submission_time_seconds Job submission time
# TYPE job_submission_time_seconds summary
job_submission_time_seconds_count 1300.0
job_submission_time_seconds_sum 650.0
job_submission_time_seconds_max 5.1
```

**Example**:
```bash
curl http://localhost:8080/actuator/prometheus
```

---

### Application Info

**Endpoint**: `GET /actuator/info`

**Description**: Get application information.

**Response**:
```json
{
  "app": {
    "name": "SimplyDone",
    "description": "Priority Job Scheduler",
    "version": "1.0.0"
  },
  "build": {
    "time": "2024-01-15T10:00:00Z",
    "version": "1.0.0"
  }
}
```

**Example**:
```bash
curl http://localhost:8080/actuator/info
```

---

## ⚠️ Error Responses

### Standard Error Format

All error responses follow this format:

```json
{
  "error": "Error type",
  "message": "Detailed error message",
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

### Common Error Types

#### Rate Limit Exceeded (429)
```json
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Maximum 10 jobs per minute allowed.",
  "retryAfter": 45,
  "limit": 10,
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

#### Validation Error (400)
```json
{
  "error": "Validation failed",
  "message": "Message cannot be empty",
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

#### Not Found (404)
```json
{
  "error": "Resource not found",
  "message": "Job not found",
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

#### Internal Server Error (500)
```json
{
  "error": "Internal server error",
  "message": "An unexpected error occurred",
  "timestamp": "2024-01-15T10:30:00.123Z"
}
```

---

## 🔧 Rate Limiting

### Rate Limit Rules

- **Default Limit**: 10 jobs per minute per user
- **Window**: Sliding 60-second window
- **Headers**: Rate limit information included in response headers

### Rate Limit Headers

When rate limits are enforced, the response includes:

```http
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 7
X-RateLimit-Reset: 1642248600
X-RateLimit-Retry-After: 45
```

### Rate Limit Status Check

Use the rate limit endpoint to check current status:

```bash
curl http://localhost:8080/api/jobs/rate-limit/user123
```

---

## 📝 Usage Examples

### Complete Job Lifecycle

```bash
# 1. Submit a job
JOB_RESPONSE=$(curl -s -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Process payment",
    "priority": "HIGH",
    "delay": 5,
    "userId": "testuser"
  }')

# Extract job ID
JOB_ID=$(echo $JOB_RESPONSE | jq -r '.id')

# 2. Check job status
curl http://localhost:8080/api/jobs/$JOB_ID

# 3. Cancel job if needed
curl -X DELETE http://localhost:8080/api/jobs/$JOB_ID
```

### Monitoring System Health

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Check system statistics
curl http://localhost:8080/api/admin/stats

# Check performance metrics
curl http://localhost:8080/api/admin/performance

# Get Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

### Queue Management

```bash
# View queue contents
curl http://localhost:8080/api/admin/queues/high
curl http://localhost:8080/api/admin/queues/low

# Clear queues (use with caution)
curl -X DELETE http://localhost:8080/api/admin/queues/clear

# Clear specific priority queue
curl -X DELETE http://localhost:8080/api/admin/queues/clear/LOW
```

### Dead Letter Queue Management

```bash
# View dead letter jobs
curl http://localhost:8080/api/admin/dead-letter-queue

# Retry a failed job
curl -X POST http://localhost:8080/api/admin/dead-letter-queue/job-123/retry

# Clear dead letter queue
curl -X DELETE http://localhost:8080/api/admin/dead-letter-queue
```

---

## 🚀 Integration Examples

### PowerShell Examples

```powershell
# Submit job
$headers = @{
    "Content-Type" = "application/json"
}
$body = @{
    message = "Process payment"
    priority = "HIGH"
    delay = 5
    userId = "powershell-user"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/api/jobs" -Method Post -Headers $headers -Body $body
Write-Host "Job submitted with ID: $($response.id)"

# Check job status
$jobDetails = Invoke-RestMethod -Uri "http://localhost:8080/api/jobs/$($response.id)" -Method Get
Write-Host "Job status: $($jobDetails.job.message)"
```

### Python Examples

```python
import requests
import json

# Submit job
url = "http://localhost:8080/api/jobs"
headers = {"Content-Type": "application/json"}
data = {
    "message": "Process payment",
    "priority": "HIGH",
    "delay": 5,
    "userId": "python-user"
}

response = requests.post(url, headers=headers, json=data)
job = response.json()
print(f"Job submitted with ID: {job['id']}")

# Check job status
job_url = f"http://localhost:8080/api/jobs/{job['id']}"
job_details = requests.get(job_url).json()
print(f"Job status: {job_details['job']['message']}")
```

### JavaScript Examples

```javascript
// Submit job
const response = await fetch('http://localhost:8080/api/jobs', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        message: 'Process payment',
        priority: 'HIGH',
        delay: 5,
        userId: 'js-user'
    })
});

const job = await response.json();
console.log(`Job submitted with ID: ${job.id}`);

// Check job status
const jobResponse = await fetch(`http://localhost:8080/api/jobs/${job.id}`);
const jobDetails = await jobResponse.json();
console.log(`Job status: ${jobDetails.job.message}`);
```

---

## 📚 Additional Resources

- [Setup Guide](SETUP_GUIDE.md)
- [Quick Reference](QUICK_REFERENCE.md)

---

**SimplyDone API v1.0** - Priority Job Scheduler 🚀
