# Troubleshooting and Maintenance

This guide covers the most common failure modes and their solutions.

---

## Startup Failures

### Port Already in Use

**Symptom**: The application fails to start with a message containing `Connector on port 8080 failed to start` or `Address already in use`.

**Solution**: Either stop the process using port 8080 or start SimplyDone on a different port by setting the `PORT` environment variable:

```bash
PORT=9090 mvn spring-boot:run
```

---

### PostgreSQL or Redis Not Reachable

**Symptom**: Application starts but the log contains `Connection refused` or `Unable to acquire JDBC Connection`.

**Solution**:
1. Check that the Docker containers are running: `docker compose ps`
2. If containers are stopped: `docker compose up -d db redis`
3. Verify ports: the `docker-compose.yml` maps PostgreSQL to host port `5433` (not the standard 5432) and Redis to host port `6380` (not the standard 6379) to avoid conflicts with locally-running instances. Confirm these ports are accessible.
4. If using external services, verify network routing and firewall rules.

---

### Application Uses H2 Instead of PostgreSQL

**Symptom**: After `docker compose up -d`, the application logs show `HikariPool ... url=jdbc:h2:mem:simplydone` instead of a PostgreSQL URL.

**Cause**: The `DATABASE_URL` environment variable is either not set or set in the wrong format. `entrypoint.sh` expects the Render URL format: `postgresql://USER:PASS@HOST:PORT/DBNAME`. If the variable is absent or uses a `jdbc:` prefix, the script skips parsing and the application falls back to H2.

**Solution**:
- For Docker Compose: ensure `DATABASE_URL=postgresql://postgres:postgres@db:5432/simplydone` in `docker-compose.yml` (not the JDBC format).
- For direct JAR execution: set `SPRING_DATASOURCE_URL=jdbc:postgresql://...` directly.

---

## Jobs Stuck in PENDING

**Symptom**: Jobs are submitted successfully (HTTP 201) but their status never changes from `PENDING`.

**Investigation**:
1. Check the application logs for the `JobWorker` class. You should see lines like `Executing job {id}: {message}` each second.
2. If no such lines appear, the worker may not be picking up jobs. Check the `executeAt` timestamp in the job response — if `delay` was large, the job will not be picked up until that time arrives.
3. Run `GET /api/admin/stats` and check `highQueueSize` and `lowQueueSize`. If they are both 0 but you just submitted a job, the job was not saved to the queue (Redis may have been unavailable at submission time).

**Solution**:
- Verify Redis connectivity by calling `GET /api/admin/health`.
- Check if the job's `executeAt` is in the future (delay was set).
- Review application logs for `Redis connection error` messages from `JobWorker`.

---

## Job Fails Immediately

**Symptom**: A job transitions from `PENDING` to `FAILED` within a few seconds.

**Investigation**: Call `GET /api/jobs/{id}` and inspect the `errorMessage` field.

**Common causes by job type**:

| Job Type | Common Error | Solution |
|---|---|---|
| `EMAIL_SEND` | "Email service is disabled" | Set `EMAIL_ENABLED=true` or provide `senderEmail`/`senderPassword` in parameters |
| `EMAIL_SEND` | "SMTP auth failed" | Verify the Gmail App Password (not your account password). Generate one at myaccount.google.com/apppasswords |
| `API_CALL` | "I/O error on GET request" | The target URL is unreachable. Verify the URL and network access from the server |
| `API_CALL` | "Expected status 200 but got 404" | The `expectedStatus` in parameters does not match the response |
| `FILE_OPERATION` | "File operation failed: null" | The path specified in `source` does not exist or is not accessible |
| `FILE_OPERATION` | "SecurityException" | The path is blocked (e.g., `/etc`, `/root`). Use a path under `/tmp` or your application directory |
| `NOTIFICATION` | "Webhook delivery failed" | The webhook URL is incorrect or the platform rejected the payload |
| `DATA_PROCESS` | "Missing required columns" | A column listed in `requiredColumns` is not present in the data |

---

## Rate Limit Errors

**Symptom**: HTTP 429 responses with `"errorCode": "RATE_LIMIT_EXCEEDED"`.

**Solution**:
- The default limit is 60 requests per minute per `userId`. If your workload requires more, increase `simplydone.scheduler.rate-limit.requests-per-minute` in `application.properties`.
- Distribute submissions across different `userId` values if possible.
- Check the current count with `GET /api/jobs/rate-limit/{userId}`. The `resetTimeSeconds` field tells you how long until the window resets.

---

## Jobs Repeatedly Failing and Moving to DLQ

**Symptom**: Jobs appear in `GET /api/admin/dead-letter-queue` after multiple retry attempts.

**Investigation**: Each DLQ entry contains the original job payload and the final `errorMessage`. Fix the underlying issue (wrong credentials, unreachable URL, bad file path), then retry the job from the Admin interface at `/admin` or via `POST /api/admin/dead-letter-queue/{jobId}/retry`.

---

## Maintenance Operations

### Checking System Health

```bash
curl http://localhost:8080/api/jobs/health
curl http://localhost:8080/api/admin/stats
curl http://localhost:8080/api/admin/health
```

### Clearing Stale Uploaded Files

Uploaded files are automatically deleted after `simplydone.upload.retention-minutes` (default: 10 minutes). To force a cleanup:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "admin",
    "jobType": "CLEANUP",
    "priority": "LOW",
    "message": "Clean up old uploads",
    "delay": 0,
    "parameters": {
      "operation": "DELETE_OLD_FILES",
      "directory": "/tmp/simplydone-uploads",
      "maxAgeHours": 1
    }
  }'
```

### Clearing All Queues (Emergency)

Via the Admin interface at `/admin` (Flush All Queues button), or via API:

```bash
curl -X DELETE http://localhost:8080/api/admin/queues/clear
```

This is irreversible. All pending jobs are lost.

### Adjusting Log Verbosity

To enable DEBUG logging for the scheduler without restarting, update `application.properties`:

```properties
logging.level.com.learnerview.SimplyDone=DEBUG
```

Relevant logger names for targeted debugging:
- `com.learnerview.SimplyDone.worker.JobWorker` — worker polling
- `com.learnerview.SimplyDone.repository.JobRepository` — Redis interactions
- `com.learnerview.SimplyDone.service.strategy` — per-job-type execution
- `com.learnerview.SimplyDone.service.impl.RateLimitingServiceImpl` — rate limit counters

