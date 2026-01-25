# SimplyDone - Complete Setup Guide

## 📋 Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Directory Structure](#directory-structure)
4. [Step-by-Step Setup](#step-by-step-setup)
5. [Configuration](#configuration)
6. [API Documentation](#api-documentation)
7. [Input/Output Formats](#inputoutput-formats)
8. [Running the Application](#running-the-application)
9. [Testing](#testing)
10. [Troubleshooting](#troubleshooting)

## 🚀 Overview

SimplyDone is a priority job scheduling system with the following features:

- **Priority-based Job Scheduling**: HIGH and LOW priority queues
- **Rate Limiting**: 10 jobs per minute per user (configurable)
- **Retry Logic**: Exponential backoff with dead letter queue
- **Monitoring**: Spring Boot Actuator + Prometheus metrics
- **Admin Management**: Comprehensive queue and user management
- **Dead Letter Queue**: Failed job inspection and manual retry

## 🔧 Prerequisites

### Required Software
- **Java 17** or higher
- **Maven 3.6** or higher
- **Redis 6.0** or higher

### Development Tools
- **IDE**: IntelliJ IDEA, Eclipse, or VS Code with Java extensions
- **Git**: For version control

## 📁 Directory Structure

```
SimplyDone/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/learnerview/SimplyDone/
│   │   │       ├── SimplyDoneApplication.java     # Main application class
│   │   │       ├── config/                        # Configuration classes
│   │   │       │   ├── JacksonConfig.java       # JSON serialization
│   │   │       │   ├── JobSchedulerHealthIndicator.java
│   │   │       │   ├── MetricsConfig.java       # Custom metrics
│   │   │       │   ├── RedisConfig.java         # Redis configuration
│   │   │       │   └── CircularDependencyConfig.java
│   │   │       ├── controller/                    # REST controllers
│   │   │       │   ├── AdminController.java     # Admin endpoints
│   │   │       │   └── JobController.java       # Job endpoints
│   │   │       ├── dto/                          # Data Transfer Objects
│   │   │       │   └── JobSubmissionRequest.java
│   │   │       ├── model/                        # Domain models
│   │   │       │   ├── Job.java
│   │   │       │   ├── JobPriority.java
│   │   │       │   └── DeadLetterJob.java
│   │   │       ├── repository/                   # Data access layer
│   │   │       │   └── JobRepository.java
│   │   │       ├── service/                      # Business logic
│   │   │       │   ├── JobService.java
│   │   │       │   ├── RetryService.java
│   │   │       │   └── RateLimitingService.java
│   │   │       └── worker/                       # Background workers
│   │   │           └── JobWorker.java
│   │   └── resources/
│   │       ├── application.properties             # Main configuration
│   │       └── application-test.properties        # Test configuration
│   └── test/
│       ├── java/
│       │   └── com/learnerview/SimplyDone/
│       │       ├── SimplyDoneApplicationTest.java
│       │       └── integration/
│       │           └── ApiTest.java
│       └── resources/
│           └── application-test.properties
├── pom.xml                                          # Maven dependencies
├── README.md                                        # Project documentation
├── SETUP_GUIDE.md                                 # This file
├── API_DOCUMENTATION.md                           # API reference
└── QUICK_REFERENCE.md                           # Quick commands reference
```

## 🛠️ Step-by-Step Setup

### Step 1: Clone the Repository
```bash
git clone <repository-url>
cd SimplyDone
```

### Step 2: Install and Start Redis

#### Option A: Using Docker (Recommended)
```bash
# Pull Redis image
docker pull redis:7-alpine

# Run Redis container
docker run -d \
  --name redis-simplydone \
  -p 6379:6379 \
  -v redis-data:/data \
  redis:7-alpine \
  redis-server --appendonly yes

# Verify Redis is running
docker exec -it redis-simplydone redis-cli ping
```

#### Option B: Using WSL/Linux
```bash
# Install Redis
sudo apt update && sudo apt install redis-server

# Start Redis
sudo service redis-server start

# Enable on boot
sudo systemctl enable redis-server

# Verify Redis
redis-cli ping
```

#### Option C: Using Windows
```powershell
# Download Redis for Windows
# Visit: https://redis.io/download
# Extract and run redis-server.exe

# Or use Chocolatey
choco install redis-64

# Start Redis
redis-server
```

### Step 3: Verify Redis Connection
```bash
# Test Redis connectivity
redis-cli -h localhost -p 6379 ping
# Should return: PONG
```

### Step 4: Build the Application
```bash
# Clean and compile
mvn clean compile

# Package the application
mvn package -DskipTests

# Verify build
ls -la target/SimplyDone-*.jar
```

### Step 5: Run the Application
```bash
# Using Maven
mvn spring-boot:run

# Or using the built JAR
java -jar target/SimplyDone-0.0.1-SNAPSHOT.jar
```

### Step 6: Verify Application Startup
The application should start with logs similar to:
```
2024-01-15 10:30:00.000  INFO  SimplyDoneApplication - Started SimplyDoneApplication
2024-01-15 10:30:00.000  INFO  JobScheduler - Circular dependencies setup completed
2024-01-15 10:30:00.000  INFO  Tomcat - Started on port(s): 8080 (http)
```

## ⚙️ Configuration

### Application Properties (`src/main/resources/application.properties`)
```properties
# Server Configuration
server.port=8080

# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000ms

# Job Scheduler Configuration
simplydone.scheduler.rate-limit.requests-per-minute=10
simplydone.scheduler.worker.interval-ms=1000
simplydone.scheduler.queues.high=jobs:high
simplydone.scheduler.queues.low=jobs:low

# Retry Configuration
simplydone.retry.max-attempts=3
simplydone.retry.backoff-multiplier=2.0
simplydone.retry.initial-delay-seconds=5

# Monitoring
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoints.health.show-details=when-authorized
management.metrics.export.prometheus.enabled=true
management.health.redis.enabled=true
```

### Test Configuration (`src/test/resources/application-test.properties`)
```properties
# Test Configuration
server.port=0
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=1
simplydone.scheduler.rate-limit.requests-per-minute=10
management.endpoints.web.exposure.include=health,metrics,prometheus
```

## 📚 API Documentation

### Base URL
```
http://localhost:8080/api
```

### Core Endpoints

#### Job Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/jobs` | Submit new job |
| GET | `/jobs/{jobId}` | Get job details |
| DELETE | `/jobs/{jobId}` | Cancel job |
| GET | `/jobs/rate-limit/{userId}` | Get rate limit status |
| GET | `/jobs/health` | Job service health |

#### Admin Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/admin/stats` | System statistics |
| GET | `/admin/queues/high` | High priority queue |
| GET | `/admin/queues/low` | Low priority queue |
| GET | `/admin/health` | System health |
| GET | `/admin/retry-stats` | Retry statistics |
| GET | `/admin/performance` | Performance metrics |
| GET | `/admin/jobs/user/{userId}` | User jobs |
| GET | `/admin/rate-limit/{userId}` | User rate limit |
| DELETE | `/admin/queues/clear` | Clear all queues |
| DELETE | `/admin/queues/clear/{priority}` | Clear specific queue |
| GET | `/admin/dead-letter-queue` | Dead letter queue |
| DELETE | `/admin/dead-letter-queue` | Clear dead letter queue |
| POST | `/admin/dead-letter-queue/{jobId}/retry` | Retry dead letter job |

#### Monitoring
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Application health |
| GET | `/actuator/metrics` | Application metrics |
| GET | `/actuator/prometheus` | Prometheus metrics |
| GET | `/actuator/info` | Application info |

## 📥 Input/Output Formats

### Job Submission Request
```json
{
  "message": "Process payment",
  "priority": "HIGH",
  "delay": 5,
  "userId": "user123"
}
```

### Job Submission Response
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Process payment",
  "priority": "HIGH",
  "delaySeconds": 5,
  "userId": "user123",
  "submittedAt": "2024-01-15T10:30:00Z",
  "executeAt": "2024-01-15T10:30:05Z",
  "status": "Job submitted successfully"
}
```

### Job Details Response
```json
{
  "success": true,
  "job": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "message": "Process payment",
    "priority": "HIGH",
    "delaySeconds": 5,
    "userId": "user123",
    "executeAt": "2024-01-15T10:30:05Z",
    "submittedAt": "2024-01-15T10:30:00Z"
  }
}
```

### System Statistics Response
```json
{
  "highQueueSize": 5,
  "lowQueueSize": 12,
  "totalQueueSize": 17,
  "executedJobs": 150,
  "rejectedJobs": 8,
  "totalProcessed": 158,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Dead Letter Job Response
```json
{
  "deadLetterJobs": [
    {
      "id": "dlq-123",
      "originalJob": {
        "id": "job-123",
        "message": "Failed job",
        "priority": "HIGH",
        "userId": "user123"
      },
      "failureReason": "Connection timeout",
      "failureTimestamp": "2024-01-15T10:30:00Z",
      "retryAttempts": 3,
      "originalPriority": "HIGH",
      "originalUserId": "user123",
      "canBeRetried": true,
      "retryCount": 1
    }
  ],
  "totalJobs": 1,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Error Response Format
```json
{
  "error": "Error type",
  "message": "Detailed error message",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Rate Limit Error Response
```json
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Maximum 10 jobs per minute allowed.",
  "retryAfter": 45,
  "limit": 10
}
```

## 🚀 Running the Application

### Development Mode
```bash
# Using Maven
mvn spring-boot:run

# Using JAR
java -jar target/SimplyDone-0.0.1-SNAPSHOT.jar
```

### With Custom Profile
```bash
# Run with specific profile
java -jar -Dspring.profiles.active=dev target/SimplyDone-0.0.1-SNAPSHOT.jar
```

### With JVM Options
```bash
java -Xms512m -Xmx1g \
     -XX:+UseG1GC \
     -jar target/SimplyDone-0.0.1-SNAPSHOT.jar
```

## 🧪 Testing

### Run All Tests
```bash
mvn test
```

### Run Specific Test
```bash
mvn test -Dtest=ApiTest
mvn test -Dtest=SimplyDoneApplicationTest
```

### Test Coverage Report
```bash
mvn jacoco:report
```

### Integration Testing
```bash
# Submit a test job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Test job",
    "priority": "HIGH",
    "delay": 5,
    "userId": "testuser"
  }'

# Check system health
curl http://localhost:8080/api/admin/health

# View queue statistics
curl http://localhost:8080/api/admin/stats

# Check dead letter queue
curl http://localhost:8080/api/admin/dead-letter-queue
```

## 🔧 Troubleshooting

### Common Issues

#### Redis Connection Failed
**Symptoms**: Application fails to start with Redis connection errors

**Solutions**:
1. Verify Redis is running: `redis-cli ping`
2. Check Redis configuration in `application.properties`
3. Verify Redis is accessible on localhost:6379
4. Check firewall settings

#### Port Already in Use
**Symptoms**: `Port 8080 was already in use`

**Solutions**:
1. Kill process using port 8080:
   ```bash
   # Windows
   netstat -ano | findstr :8080
   taskkill /F /PID <PID>
   
   # Linux/Mac
   lsof -ti:8080 | xargs kill -9
   ```
2. Change port in `application.properties`:
   ```properties
   server.port=8081
   ```

#### Job Submission Fails
**Symptoms**: Internal Server Error when submitting jobs

**Solutions**:
1. Check Redis connectivity
2. Verify JSON format in request
3. Check rate limit status
4. Review application logs for detailed error

#### Rate Limiting Issues
**Symptoms**: 429 Too Many Requests

**Solutions**:
1. Wait for rate limit window to reset (60 seconds)
2. Check current rate limit status:
   ```bash
   curl http://localhost:8080/api/jobs/rate-limit/username
   ```
3. Adjust rate limit in configuration

### Health Check Failures
**Symptoms**: Health endpoint returns DOWN status

**Solutions**:
1. Check Redis health: `curl http://localhost:8080/actuator/health`
2. Verify queue sizes are not overloaded
3. Check application logs for specific errors

### Performance Issues
**Symptoms**: Slow response times or high memory usage

**Solutions**:
1. Monitor JVM metrics: `curl http://localhost:8080/actuator/metrics`
2. Check queue sizes and clear if necessary
3. Adjust worker polling interval
4. Monitor Redis memory usage

### Log Analysis
```bash
# View application logs
tail -f logs/simplydone.log

# Check for errors
grep -i error logs/simplydone.log

# Monitor Redis operations
grep -i redis logs/simplydone.log
```

### Docker Issues
```bash
# Check container logs
docker logs simplydone-app

# Check container status
docker ps -a | grep simplydone

# Inspect container
docker inspect simplydone-app
```

## 📞 Monitoring and Observability

### Health Checks
```bash
# Application health
curl http://localhost:8080/actuator/health

# Detailed health
curl http://localhost:8080/actuator/health?showDetails=true
```

### Metrics Collection
```bash
# Available metrics
curl http://localhost:8080/actuator/metrics

# Specific metric
curl http://localhost:8080/actuator/metrics/job.execution.time
```

### Prometheus Integration
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'simplydone'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s
```

### Grafana Dashboard
- Import SimplyDone dashboard template
- Configure data source for Prometheus
- Monitor key metrics:
  - Job execution rate
  - Queue sizes
  - Success/failure rates
  - Redis performance

## 📚 Additional Documentation

- **README.md**: Project overview and quick start
- **API_DOCUMENTATION.md**: Complete API reference
- **QUICK_REFERENCE.md**: Essential commands and examples

## 🤝 Support

For issues and questions:

1. Check application logs for detailed error messages
2. Verify Redis connectivity and configuration
3. Review this troubleshooting guide
4. Check GitHub Issues for known problems
5. Consult the API documentation for endpoint usage

---

**SimplyDone is now ready for use!** 🚀
