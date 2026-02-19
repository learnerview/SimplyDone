# Architecture

This document describes the design, components, and data flow of SimplyDone.

## System overview

SimplyDone is a three-tier distributed job scheduling system:

```
┌─────────────────────────────────────────────────────────┐
│                   Client Layer                           │
│              (Web UI + REST API Clients)                 │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Application Layer                           │
│         (Spring Boot 3.2 + Thymeleaf UI)                 │
│  ┌─────────────┐         ┌──────────────┐              │
│  │  REST API   │         │  Web UI      │              │
│  │  Controller │         │  Controller  │              │
│  └──────┬──────┘         └──────┬───────┘              │
│         │                       │                       │
│  ┌──────▼──────────────────────▼────────┐              │
│  │   Job Service Layer                   │              │
│  │   (Submission, Validation, Auth)      │              │
│  └──────┬──────────────────────┬────────┘              │
│         │                      │                       │
│  ┌──────▼────┐    ┌───────────▼─────────┐             │
│  │ Queue Svc │    │ Strategy Executor   │             │
│  │(HIGH/LOW) │    │ (7 job types)       │             │
│  └──────┬────┘    └────────┬────────────┘             │
│         │                  │                          │
└─────────┼──────────────────┼──────────────────────────┘
          │                  │
┌─────────▼──────────────────▼──────────────────────────┐
│              Data Storage Layer                       │
│   ┌─────────────────┐      ┌──────────────────┐     │
│   │ Redis (Queues)  │      │ PostgreSQL (Log) │     │
│   │ - Jobs          │      │ - Job History    │     │
│   │ - Dead Letter   │      │ - Executions     │     │
│   │ - Stats         │      │ - Audit Trail    │     │
│   └─────────────────┘      └──────────────────┘     │
└──────────────────────────────────────────────────────┘
```

## Request flow

### Job submission

```
Client
  │
  ├─ REST API (POST /api/jobs)
  │     OR
  └─ Web Form (POST /email-send, etc.)
       │
       ▼
JobController
       │
       ├─ Validate input (required fields, constraints)
       ├─ Check rate limit (user quota)
       ├─ Generate job ID
       │
       ▼
JobService.submitJob()
       │
       ├─ Create Job entity
       ├─ Calculate execution time (now + delay)
       ├─ Persist to PostgreSQL
       │
       ▼
JobQueueService
       │
       ├─ Determine priority (HIGH or LOW)
       ├─ Push to Redis queue
       │   (using ZADD for sorted set with score = execution time)
       │
       ▼
Response to Client
       │
       └─ Job ID + status (SUBMITTED)
```

### Job execution

```
Redis Queue (HIGH/LOW)
       │
       ▼
Job Worker Thread (background)
       │
       ├─ Poll every 1000ms (configurable)
       ├─ Check HIGH queue first
       ├─ Fetch jobs with execution time <= now
       │
       ▼
JobExecutor
       │
       ├─ Look up job in PostgreSQL
       ├─ Determine job type
       │
       ▼
Strategy Pattern Resolution
       │
       ├─ EmailSendStrategy
       ├─ DataProcessStrategy
       ├─ ApiCallStrategy
       ├─ FileOperationStrategy
       ├─ NotificationStrategy
       ├─ ReportGenerationStrategy
       └─ CleanupStrategy
       │
       ▼
Strategy.execute(jobRequest)
       │
       ├─ Execute job logic
       ├─ Catch exceptions
       │
       ▼
Result → Update PostgreSQL
       │
       ├─ If success:
       │  - Mark EXECUTED
       │  - Store output (if any)
       │  - Update stats (jobs_executed counter)
       │
       ├─ If failure:
       │  - Increment retry count
       │  - If retries < 3:
       │    - Re-queue with exponential backoff
       │  - If retries >= 3:
       │    - Move to dead-letter queue
       │    - Mark FAILED
       │    - Store error message
```

## Data model

### Job entity (PostgreSQL)

```java
@Entity
@Table(name = "job")
public class Job {
    @Id
    Long id;                          // Unique identifier
    
    String jobId;                     // String ID returned to client
    JobType jobType;                  // EMAIL_SEND, API_CALL, etc.
    JobStatus status;                 // PENDING, EXECUTING, EXECUTED, FAILED
    Priority priority;                // HIGH or LOW
    
    String message;                   // Human-readable description
    String userId;                    // Submitter identifier
    String parameters;                // JSON object as string
    
    LocalDateTime submittedAt;        // Submission timestamp
    LocalDateTime executeAt;          // Scheduled execution time
    LocalDateTime executedAt;         // Actual execution time
    
    Integer retryCount;               // Current retry attempt
    String errorMessage;              // Error details if failed
    String output;                    // Execution result (if applicable)
}
```

### Job Queue (Redis)

```
Redis Data Structure:
  ├─ simplydone:jobs:high    (Sorted Set)
  │  └─ Members: Job IDs
  │     Scores: Epoch timestamp of execution time
  │
  ├─ simplydone:jobs:low     (Sorted Set)
  │  └─ Members: Job IDs
  │     Scores: Epoch timestamp of execution time
  │
  ├─ dead_letter:jobs        (List)
  │  └─ Members: Job IDs of failed jobs
  │
  ├─ stats:executed          (Counter)
  │  └─ Value: Total successfully executed jobs
  │
  └─ stats:rejected          (Counter)
     └─ Value: Total rate-limited submissions
```

### Rate limiting (Redis)

Each user has a sliding window counter:

```
Key: simplydone:ratelimit:{userId}
Type: Sorted Set
Members: Request timestamps (epoch milliseconds)
Operation:
  1. Add current timestamp
  2. Remove timestamps older than 1 minute
  3. Count remaining members
  4. If count > requests_per_minute: reject with 429
```

## Component architecture

### Controllers (HTTP entry points)

**JobController** (`/api/jobs`)
- POST /api/jobs — Submit new job
- GET /api/jobs/{id} — Get job status
- DELETE /api/jobs/{id} — Cancel pending job
- GET /api/jobs/health — Health check

**AdminController** (`/api/admin`)
- GET /api/admin/stats — System statistics
- GET /api/admin/queues/high — HIGH priority queue contents
- GET /api/admin/queues/low — LOW priority queue contents
- GET /api/admin/dead-letter-queue — Failed jobs
- POST /api/admin/dead-letter-queue/retry/{id} — Retry failed job
- POST /api/admin/queues/clear — Clear a queue

**FileUploadController** (`/api/files`)
- POST /api/files/upload — Upload file
- GET /api/files/list — List uploaded files
- DELETE /api/files/{id} — Delete uploaded file

**WebViewController** (HTML pages)
- GET / — Dashboard
- GET /jobs — Jobs page
- GET /job-status — Job lookup
- GET /admin — Admin page
- GET /email-send, /api-call, etc. — Job submission forms

### Services (business logic)

**JobService**
- `submitJob()` — Validate and persist job, publish event
- `getJob()` — Retrieve job by ID
- `getJobStatus()` — Get current status and details
- `updateJobStatus()` — Update status after execution

**JobQueueService**
- `enqueue()` — Push job to appropriate queue
- `dequeue()` — Pop next job from queue (worker thread)
- `requeue()` — Re-add job for retry
- `getQueueSize()` — Count jobs in queue

**JobExecutorService**
- `execute()` — Main execution logic, delegates to strategy
- `executeWithRetry()` — Retry logic with exponential backoff

**FileUploadService & FileCleanupScheduler**
- `uploadFile()` — Save multipart file to disk
- `deleteFile()` — Remove file by ID
- `cleanup()` — Scheduled task to delete old files (every minute)

**RateLimitService**
- `checkLimit()` — Verify user hasn't exceeded quota
- `recordRequest()` — Add timestamp for sliding window

### Strategies (job type handlers)

```
JobStrategy interface:
  └─ execute(JobRequest) → JobResult

Implementations:
  ├─ EmailSendStrategy      - Resend API
  ├─ DataProcessStrategy    - CSV operations
  ├─ ApiCallStrategy        - HTTP requests
  ├─ FileOperationStrategy  - File system ops
  ├─ NotificationStrategy   - Webhooks
  ├─ ReportGenerationStrategy - Document generation
  └─ CleanupStrategy        - Maintenance tasks
```

Each strategy:
1. Receives job parameters
2. Validates inputs
3. Executes operation
4. Returns result or throws exception
5. Exception caught by executor → retry or dead-letter

### Worker (background job processing)

```java
@Component
public class JobWorker {
    @Scheduled(fixedRateString = "${simplydone.scheduler.worker.interval-ms}")
    public void poll() {
        // 1. Fetch job from queue
        Job job = jobQueueService.dequeue();
        
        // 2. Skip if not ready (execution time in future)
        if (job.executeAt > now()) return;
        
        // 3. Execute job
        try {
            JobResult result = jobExecutor.execute(job);
            jobService.markExecuted(job, result);
        } catch (Exception e) {
            if (job.retryCount < 3) {
                jobQueueService.requeue(job); // Retry
            } else {
                jobQueueService.moveToDeadLetter(job, e);
            }
        }
    }
}
```

Runs every 1 second by default (configurable).

## Database schema

### Tables

**job** (main table)
```sql
CREATE TABLE job (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(255) UNIQUE,
    job_type VARCHAR(50),
    status VARCHAR(50),
    priority VARCHAR(10),
    message TEXT,
    user_id VARCHAR(255),
    parameters JSON,
    submitted_at TIMESTAMP,
    execute_at TIMESTAMP,
    executed_at TIMESTAMP,
    retry_count INTEGER,
    error_message TEXT,
    output TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_job_id ON job(job_id);
CREATE INDEX idx_status ON job(status);
CREATE INDEX idx_execute_at ON job(execute_at);
CREATE INDEX idx_user_id ON job(user_id);
```

**job_execution** (audit log)
```sql
CREATE TABLE job_execution (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(255),
    status VARCHAR(50),
    duration_ms INTEGER,
    error_message TEXT,
    executed_at TIMESTAMP DEFAULT NOW(),
    
    FOREIGN KEY (job_id) REFERENCES job(job_id)
);

CREATE INDEX idx_job_execution_job_id ON job_execution(job_id);
CREATE INDEX idx_executed_at ON job_execution(executed_at);
```

## Concurrency

### Thread safety

**PostgreSQL**
- Spring Data JPA transactions provide ACID guarantees
- Each job submission uses separate transaction
- Isolation prevents duplicate processing

**Redis**
- Redis single-threaded, operations atomic
- No race conditions on queue operations
- Sorted set scores ensure order

**Worker thread**
- Single background worker polls queues
- One job processed at a time
- No multi-threaded executor (prevents resource exhaustion)

### Distributed deployment

If running multiple instances:

1. **All instances share same PostgreSQL** — Single source of truth
2. **All instances share same Redis** — Shared queues
3. **Multiple worker threads** — One per instance
   - Both pull from same queues
   - Redis ZRANGE + DEL ensures no duplication
   - Database primary key prevents double-logging

For true scale, use Kubernetes with horizontal pod autoscaling.

## Error handling and resilience

### Retry logic

Jobs that fail are retried automatically:

1. First failure → Retry after 1 second
2. Second failure → Retry after 2 seconds
3. Third failure → Retry after 4 seconds
4. Fourth failure → Move to dead-letter queue

Dead-letter queue allows manual inspection and retry.

### Error propagation

```
Exception in strategy
       │
       ▼
Executor catches exception
       │
       ├─ Log error
       ├─ Create ErrorReport
       ├─ Update job status to FAILED
       ├─ Store error message in database
       │
       ▼
Retry decision:
   if (retryCount < 3)
       ├─ Requeue with exponential backoff
   else
       └─ Move to dead-letter queue
```

### Health checks

Application exposes `/actuator/health` which returns:

```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},        // PostgreSQL connection
    "redis": {"status": "UP"},     // Redis connection
    "diskSpace": {"status": "UP"}   // Disk space for uploads
  }
}
```

Deployment platforms use this endpoint to:
- Route traffic only to healthy instances
- Restart unhealthy containers
- Report status to monitoring systems

## Performance characteristics

### Scalability limits

**PostgreSQL**
- Supports ~10,000 jobs/day without tuning
- Connection pool: 10 connections (configurable)
- Query response: <100ms typical

**Redis**
- Supports ~100,000 jobs/second (single instance)
- Memory usage: <100MB for 10,000 queued jobs
- Lookup: O(log N) for sorted set operations

**Memory usage**
- Base heap: 256MB
- Per job: ~5KB average
- Uploads: Limited by disk, not memory

**CPU usage**
- Idle: <5%
- Processing: 10-20% per job
- Worker polling: <1% overhead

### Tuning

**High throughput**
```properties
# More frequent polling
simplydone.scheduler.worker.interval-ms=100

# Larger database pool
spring.datasource.hikari.maximum-pool-size=20

# More upload disk space
simplydone.upload.max-size-mb=500
```

**Low memory**
```properties
# Less frequent polling
simplydone.scheduler.worker.interval-ms=5000

# Smaller database pool
spring.datasource.hikari.maximum-pool-size=5

# Smaller JVM heap
# -Xmx256m (in startup script)
```

## Extensibility

### Adding a new job type

1. Create strategy class:
```java
@Component
public class MyJobStrategy implements JobStrategy {
    public JobResult execute(JobRequest request) {
        // Implementation
    }
}
```

2. Register in executor
3. Add corresponding UI page
4. Add documentation

### Custom storage

Replace Redis/PostgreSQL:
- Implement RepositorySPI interfaces
- Inject custom implementation
- Same application code works unchanged

### Custom authentication

Extend `SecurityConfiguration` to:
- Use OAuth2, OIDC, LDAP, etc.
- Protect `/api/admin` routes
- Control view access in web UI

## Deployment patterns

### Blue-green deployment
- Run two identical instances
- Direct traffic to "blue"
- Deploy new version to "green"
- Switch traffic atomically
- No downtime

### Canary deployment
- Route 10% traffic to new version
- Monitor error rates, latency
- Gradually increase traffic (10% → 100%)
- Automatic rollback if errors spike

### Rolling deployment
- Update instance N, then N+1
- Maintain minimum replicas during update
- Health checks verify each instance
- Automatic rollback on health check failure

## References

- [Spring Boot 3.2 docs](https://spring.io/projects/spring-boot)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [Redis Sorted Sets](https://redis.io/topics/data-types)
