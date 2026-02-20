# Getting Started with SimplyDone

This guide walks through every step required to get SimplyDone running locally and to verify each component of the system is working correctly.

---

## System Prerequisites

| Requirement | Minimum Version | Check Command |
|---|---|---|
| Java | 17 | `java -version` |
| Maven | 3.8 | `mvn -version` |
| Docker | 24 | `docker --version` |
| Docker Compose | 2.0 | `docker compose version` |

---

## Option A: Full Docker Compose Stack

This option runs the application, PostgreSQL, and Redis all inside Docker. It is the quickest way to get a working environment.

### Step 1: Clone the Repository

```bash
git clone https://github.com/learnerview/SimplyDone.git
cd SimplyDone
```

### Step 2: Create an Environment File

```bash
cp .env.template .env
```

Open `.env` and review the defaults. To enable email sending, set `SMTP_USERNAME`, `SMTP_PASSWORD`, and `EMAIL_ENABLED=true`. For local testing without email, leave the defaults.

### Step 3: Start All Services

```bash
docker compose up -d
```

This starts three containers:
- `app`: the Spring Boot application on port 8080
- `db`: PostgreSQL 15 on port 5433
- `redis`: Redis 7 on port 6380

### Step 4: Verify All Containers Are Running

```bash
docker compose ps
```

All three services should show a status of `running`.

---

## Option B: Maven with Local Infrastructure

Use this approach when you need to develop and rebuild the application quickly without rebuilding the Docker image each time.

### Step 1: Start Only the Infrastructure Containers

```bash
docker compose up -d db redis
```

### Step 2: Build the Application

```bash
mvn clean install -DskipTests
```

Maven downloads all dependencies and packages the application JAR into `target/`.

### Step 3: Run the Application

```bash
mvn spring-boot:run
```

The application connects to PostgreSQL on `localhost:5433` and Redis on `localhost:6380` (the ports exposed by the Docker containers).

---

## Verifying the Installation

### Health Check

```bash
curl http://localhost:8080/api/jobs/health
```

Expected response:

```json
{
  "status": 200,
  "success": true,
  "message": "Job service is operational and ready to accept jobs",
  "path": "/api/jobs/health",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Dashboard

Open `http://localhost:8080` in a browser. You should see the job submission form and two empty priority queues.

### Submit a Test Job

The following command submits an API_CALL job that will execute immediately. Because external network access may be restricted in your environment, this job may fail — that is expected and demonstrates the error reporting pipeline correctly.

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "jobType": "API_CALL",
    "priority": "HIGH",
    "message": "Verify the API_CALL strategy",
    "delay": 2,
    "parameters": {
      "url": "https://httpbin.org/get",
      "method": "GET",
      "expectedStatus": 200
    }
  }'
```

Note the `id` field in the response, then poll the job status:

```bash
curl http://localhost:8080/api/jobs/<id-from-above>
```

The `status` field transitions from `PENDING` to `EXECUTED` (on success) or `FAILED` (on error).

### Submit an Immediate File Operation (no network required)

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "test-user",
    "jobType": "FILE_OPERATION",
    "priority": "HIGH",
    "message": "Create a test directory",
    "delay": 0,
    "parameters": {
      "operation": "CREATE_DIRECTORY",
      "source": "/tmp/simplydone-test"
    }
  }'
```

After a few seconds, the job status endpoint should show `"status": "EXECUTED"` and the directory `/tmp/simplydone-test` should exist on the host running the application.

---

## Next Steps

- [REST API Reference](REST_API_REFERENCE.md) - full endpoint documentation with request/response shapes
- [User Interface Guide](USER_INTERFACE_GUIDE.md) - walkthrough of every page in the web interface
- [System Configuration](SYSTEM_CONFIGURATION.md) - all configurable properties and their defaults
- [Job Type Catalog](JOB_TYPE_CATALOG.md) - parameters supported by each job type

