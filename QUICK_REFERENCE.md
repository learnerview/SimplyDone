# SimplyDone - Quick Reference

## 🚀 Quick Start

### Start Application
```bash
# Start Redis
docker run -d -p 6379:6379 --name redis redis:7-alpine redis-server --appendonly yes

# Start SimplyDone
cd SimplyDone
mvn spring-boot:run
```

### Verify Installation
```bash
# Health check
curl http://localhost:8080/api/admin/health

# Submit a job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"message":"Test job","priority":"HIGH","delay":5,"userId":"testuser"}'
```

## 📋 Essential Commands

### Maven Commands
```bash
mvn clean compile              # Clean and compile
mvn package -DskipTests          # Build JAR
mvn test                       # Run tests
mvn spring-boot:run            # Run application
```

### Redis Commands
```bash
redis-cli ping                   # Test Redis connection
redis-cli keys "*"              # List all keys
redis-cli flushall              # Clear all data
redis-cli monitor               # Monitor Redis operations
```

### Application Commands
```bash
# Health check
curl http://localhost:8080/api/admin/health

# System stats
curl http://localhost:8080/api/admin/stats

# Clear queues
curl -X DELETE http://localhost:8080/api/admin/queues/clear

# Dead letter queue
curl http://localhost:8080/api/admin/dead-letter-queue
```

## 🔑 API Endpoints

### Core Jobs API
```bash
# Submit job
POST /api/jobs
Content-Type: application/json
{
  "message": "string",
  "priority": "HIGH|LOW",
  "delay": "number",
  "userId": "string"
}

# Get job details
GET /api/jobs/{jobId}

# Cancel job
DELETE /api/jobs/{jobId}

# Rate limit status
GET /api/jobs/rate-limit/{userId}
```

### Admin API
```bash
# System statistics
GET /api/admin/stats

# Queue inspection
GET /api/admin/queues/high
GET /api/admin/queues/low

# Health check
GET /api/admin/health

# Performance metrics
GET /api/admin/performance

# Clear queues
DELETE /api/admin/queues/clear
DELETE /api/admin/queues/clear/{priority}

# Dead letter queue
GET /api/admin/dead-letter-queue
DELETE /api/admin/dead-letter-queue
POST /api/admin/dead-letter-queue/{jobId}/retry
```

### Monitoring API
```bash
# Application health
GET /actuator/health

# Metrics
GET /actuator/metrics

# Prometheus metrics
GET /actuator/prometheus

# Application info
GET /actuator/info
```

## 📝 Request/Response Examples

### Submit Job Request
```json
{
  "message": "Process payment",
  "priority": "HIGH",
  "delay": 5,
  "userId": "user123"
}
```

### Submit Job Response
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

### Error Response
```json
{
  "error": "Rate limit exceeded",
  "message": "Too many requests. Maximum 10 jobs per minute allowed.",
  "retryAfter": 45,
  "limit": 10
}
```

## ⚙️ Configuration

### Key Properties
```properties
# Server
server.port=8080

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Rate Limiting
simplydone.scheduler.rate-limit.requests-per-minute=10

# Retry
simplydone.retry.max-attempts=3
simplydone.retry.backoff-multiplier=2.0
simplydone.retry.initial-delay-seconds=5

# Monitoring
management.endpoints.web.exposure.include=health,metrics,prometheus
```

### Environment Variables
```bash
SERVER_PORT=8080
REDIS_HOST=localhost
REDIS_PORT=6379
SPRING_PROFILES_ACTIVE=dev
```

## 🔍 Troubleshooting

### Common Issues

#### Redis Connection Failed
```bash
# Check Redis
redis-cli ping

# Check logs
tail -f logs/simplydone.log | grep -i redis
```

#### Port Already in Use
```bash
# Find process
netstat -ano | findstr :8080

# Kill process
taskkill /F /PID <PID>
```

#### Job Submission Fails
```bash
# Check rate limit
curl http://localhost:8080/api/jobs/rate-limit/testuser

# Check health
curl http://localhost:8080/actuator/health
```

### Performance Issues
```bash
# Check metrics
curl http://localhost:8080/actuator/metrics/job.execution.time

# Check queue sizes
curl http://localhost:8080/api/admin/stats
```

## 📊 Monitoring

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Key Metrics
```bash
# Job execution time
curl http://localhost:8080/actuator/metrics/job.execution.time

# Queue sizes
curl http://localhost:8080/actuator/metrics/simplydone.queue.size

# Success rate
curl http://localhost:8080/api/admin/performance
```

### Prometheus Metrics
```bash
curl http://localhost:8080/actuator/prometheus | grep job_
```

## 🚀 Running the Application

### Development Mode
```bash
# Using Maven
mvn spring-boot:run

# Using JAR
java -jar target/SimplyDone-0.0.1-SNAPSHOT.jar
```

### With JVM Options
```bash
java -Xms512m -Xmx1g \
     -XX:+UseG1GC \
     -jar target/SimplyDone-0.0.1-SNAPSHOT.jar
```

## 🧪 Testing

### Run Tests
```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=ApiTest
```

### Integration Testing
```bash
# Submit test job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"message":"Test job","priority":"HIGH","delay":5,"userId":"test"}'

# Check system health
curl http://localhost:8080/api/admin/health
```

## 📚 Documentation Links

- [Complete Setup Guide](SETUP_GUIDE.md)
- [API Documentation](API_DOCUMENTATION.md)
- [README](README.md)

---

**SimplyDone - Priority Job Scheduler** 🚀
