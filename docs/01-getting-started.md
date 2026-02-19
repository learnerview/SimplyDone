# Getting Started with SimplyDone

This guide walks you through setting up SimplyDone on your local machine for development and testing.

## Prerequisites

| Requirement | Version | Check command | Notes |
|---|---|---|---|
| Java | 17+ | `java -version` | Required for both build and runtime |
| Maven | 3.8+ | `mvn -version` | Required for dependency management and build |
| Docker | 24+ | `docker --version` | Required for PostgreSQL and Redis containers |
| Docker Compose | 2.0+ | `docker compose version` | Usually installed with Docker Desktop |

## 1. Clone or download the repository

Clone from your Git repository:
```bash
git clone <your-repository-url>
cd SimplyDone
```

Or download the source code and extract it:
```bash
cd SimplyDone
```

## 2. Start dependencies with Docker

SimplyDone requires Redis and PostgreSQL. Use the included `docker-compose.yml` to start both in containers:

```bash
docker compose up -d
```

This starts two services:
- **PostgreSQL 15** — Runs on port **5433** (not standard 5432 to avoid conflicts)
  - Database: `simplydone`
  - User: `postgres`
  - Password: `postgres`
  - Storage: Persistent volume `simplydone-postgres-data`

- **Redis 7** — Runs on port **6380** (not standard 6379 to avoid conflicts)
  - No authentication required for local development
  - Storage: Persistent volume `simplydone-redis-data`

### Verify containers are running

```bash
docker ps
```

You should see two running containers:
```
CONTAINER ID   IMAGE                    STATUS
abc123...      postgres:15              Up X seconds
def456...      redis:7                  Up X seconds
```

### Check container health

```bash
# PostgreSQL
docker exec simplydone-postgres pg_isready -U postgres

# Redis
docker exec simplydone-redis redis-cli ping
```

Both should respond affirmatively.

## 3. Build the application

Maven downloads dependencies and compiles the source code:

```bash
mvn clean install
```

This step:
- Downloads all dependencies from Maven Central
- Compiles Java sources
- Runs unit tests
- Packages the application

First build may take 2-5 minutes depending on internet speed. Subsequent builds are faster.

## 4. Run the application

Start the application with Spring Boot's development server:

```bash
mvn spring-boot:run
```

The application starts on **port 8080**. Watch for this message indicating successful startup:

```
Started SimplyDoneApplication in X.XXX seconds (JVM running for Y.YYY)
Job worker started, polling every 1000ms
```

If you see this message, the application is ready to receive requests.

## 5. Verify the application

### Via web browser
Open your browser to:
```
http://localhost:8080
```

You should see the SimplyDone dashboard with:
- System health indicators
- Live queue depth counter
- Job submission form

### Via health endpoint
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "diskSpace": {"status": "UP"}
  }
}
```

### Via application info endpoint
```bash
curl http://localhost:8080/actuator/info
```

## 6. Submit your first job

Open the dashboard at `http://localhost:8080`, fill out the form, and click Submit. Or use curl:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Call external API",
    "priority": "HIGH",
    "delay": 0,
    "userId": "demo-user",
    "jobType": "API_CALL",
    "parameters": {
      "url": "https://httpbin.org/delay/1",
      "method": "GET"
    }
  }'
```

Response includes a job ID:
```json
{
  "id": "job-f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "status": "SUBMITTED",
  "submittedAt": "2024-01-15T10:30:45Z",
  "executeAt": "2024-01-15T10:30:45Z",
  "message": "Job submitted successfully"
}
```

Copy the job ID and check its status:
```bash
curl http://localhost:8080/api/jobs/job-f47ac10b-58cc-4372-a567-0e02b2c3d479
```

## Available profiles

### Local profile (default)

Used during development with Docker containers on localhost.

```bash
mvn spring-boot:run
# Activates application-local.properties
```

Configuration:
- Database: `postgresql://postgres:postgres@localhost:5433/simplydone`
- Redis: `redis://localhost:6380`
- Email: Disabled (EMAIL_ENABLED=false)
- Upload dir: OS temp directory

### Production profile

Used for testing production configuration. Requires environment variables:

```powershell
# PowerShell
$env:SPRING_PROFILES_ACTIVE='prod'
$env:DATABASE_URL='jdbc:postgresql://localhost:5433/simplydone'
$env:DATABASE_USER='postgres'
$env:DATABASE_PASSWORD='postgres'
$env:REDIS_URL='redis://localhost:6380'
$env:EMAIL_ENABLED='false'
$env:EMAIL_API_KEY='test_key'
mvn spring-boot:run
```

Or bash:
```bash
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL='jdbc:postgresql://localhost:5433/simplydone'
export DATABASE_USER='postgres'
export DATABASE_PASSWORD='postgres'
export REDIS_URL='redis://localhost:6380'
export EMAIL_ENABLED=false
export EMAIL_API_KEY=test_key
mvn spring-boot:run
```

## Next steps

### Explore the web UI
- Dashboard: `http://localhost:8080`
- Jobs: `http://localhost:8080/jobs`
- Admin: `http://localhost:8080/admin`

### Read the documentation
- [API Reference](03-api-reference.md) — All REST endpoints
- [Job Types](04-job-types.md) — Parameters and examples for each job type
- [Configuration](05-configuration.md) — All available properties

### Run comprehensive tests
```powershell
# PowerShell
.\scripts\test-comprehensive.ps1 -BaseUrl "http://localhost:8080"
```

### Prepare for deployment
See [Deployment Guide](06-deployment-guide.md) for deploying to Render, AWS, Docker, or self-hosted environments.

## Troubleshooting

### Maven build fails with dependency errors
- Your local Maven cache may be corrupted. Clear it:
  ```bash
  mvn clean
  rm -rf ~/.m2/repository
  mvn install
  ```

### Containers won't start
```bash
# Check Docker is running
docker ps

# Check for port conflicts
docker ps -a | grep simplydone

# Remove any stopped containers
docker compose down -v

# Start fresh
docker compose up -d
```

### Application fails to connect to PostgreSQL or Redis
```bash
# Check container health
docker compose ps

# Check logs
docker compose logs postgres
docker compose logs redis

# Verify connection
docker exec simplydone-postgres psql -U postgres -c "SELECT 1"
docker exec simplydone-redis redis-cli ping
```

### Port 8080 already in use
```bash
# Find process using port 8080
netstat -ano | findstr :8080  # Windows
lsof -i :8080                  # macOS/Linux

# Or change the port
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

### Application logs show connection errors
Check that Docker containers are running:
```bash
docker compose ps
```

Containers must show "healthy" status. If not, restart them:
```bash
docker compose down
docker compose up -d
```

## Development tips

### Hot reload (auto-restart on code changes)
Use Spring Boot DevTools for automatic restart:
```bash
mvn spring-boot:run
# Changes to src/main/java files trigger automatic restart
```

### Enable SQL logging
Add to `application.properties` to see generated SQL:
```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### View Redis data
```bash
docker exec -it simplydone-redis redis-cli
> KEYS *
> GET simplydone:jobs:high
> LRANGE simplydone:jobs:high 0 -1
```

### View PostgreSQL data
```bash
docker exec -it simplydone-postgres psql -U postgres -d simplydone
simplydone=# SELECT * FROM job;
simplydone=# SELECT * FROM job_execution;
```

### Access application logs
```bash
# Application logs
tail -f logs_debug.txt

# Or view in app terminal output
# Logs display in the mvn spring-boot:run terminal window
```

## Clean up

### Stop containers without removing them
```bash
docker compose stop
```

### Stop and remove containers
```bash
docker compose down
```

### Remove containers and delete persistent data
```bash
docker compose down -v
```

This deletes the job database and queue history. On next `docker compose up`, containers will start fresh.

## Next: Production deployment

When ready to deploy:
1. Read [Deployment Guide](06-deployment-guide.md)
2. Configure render.yaml for your infrastructure
3. Deploy to Render, AWS, or your chosen platform
