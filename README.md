# SimplyDone - Priority Job Scheduler

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)](https://github.com/your-repo/simplydone)
[![Java Version](https://img.shields.io/badge/java-17+-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/spring%20boot-3.2.1-green.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/redis-6.0+-red.svg)](https://redis.io/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A priority job scheduling system built with Spring Boot and Redis, featuring intelligent retry logic, rate limiting, and comprehensive monitoring.

## ✨ Features

### 🚀 Core Functionality
- **Priority-based Scheduling**: HIGH and LOW priority queues with FIFO ordering
- **Intelligent Retry Logic**: Exponential backoff with configurable max attempts
- **Dead Letter Queue**: Failed job inspection and manual retry capabilities
- **Rate Limiting**: User-based rate limiting (10 jobs/minute by default)
- **Real-time Monitoring**: Spring Boot Actuator + Prometheus metrics

### 🛠️ Advanced Features
- **Admin Management**: Complete queue and user management APIs
- **Scalable Architecture**: Redis-backed with horizontal scaling support
- **Error Handling**: Robust error handling with detailed error responses
- **Performance Optimized**: Efficient Redis operations and connection pooling

### 📊 Monitoring & Observability
- **Health Checks**: Application, Redis, and custom health indicators
- **Metrics Collection**: Job execution, submission, and system metrics
- **Prometheus Integration**: Ready for monitoring

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client Apps   │───▶│  Spring Boot    │───▶│     Redis       │
│                 │    │   Application   │    │   Job Queues    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │                        │
                              ▼                        ▼
                       ┌─────────────────┐    ┌─────────────────┐
                       │   Dead Letter   │    │   Rate Limit    │
                       │      Queue       │    │     Store       │
                       └─────────────────┘    └─────────────────┘
```

## 🚀 Quick Start

### Prerequisites
- **Java 17+**
- **Maven 3.6+**
- **Redis 6.0+**

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-repo/simplydone.git
   cd simplydone
   ```

2. **Start Redis**
   ```bash
   # Using Docker (recommended)
   docker run -d -p 6379:6379 --name redis redis:7-alpine redis-server --appendonly yes
   
   # Or install locally
   # Ubuntu/Debian: sudo apt install redis-server
   # macOS: brew install redis
   # Windows: Download from redis.io
   ```

3. **Run the application**
   ```bash
   mvn spring-boot:run
   ```

4. **Verify installation**
   ```bash
   curl http://localhost:8080/api/admin/health
   ```

### First Job

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "message": "My first job",
    "priority": "HIGH",
    "delay": 5,
    "userId": "testuser"
  }'
```

## 📚 Documentation

| Document | Description |
|----------|-------------|
| [📖 Setup Guide](SETUP_GUIDE.md) | Complete installation and configuration guide |
| [🔧 API Documentation](API_DOCUMENTATION.md) | Comprehensive API reference |
| [⚡ Quick Reference](QUICK_REFERENCE.md) | Essential commands and examples |

## 🔑 API Overview

### Job Management
```bash
# Submit job
POST /api/jobs

# Get job details
GET /api/jobs/{jobId}

# Cancel job
DELETE /api/jobs/{jobId}

# Rate limit status
GET /api/jobs/rate-limit/{userId}
```

### Admin Operations
```bash
# System statistics
GET /api/admin/stats

# Queue inspection
GET /api/admin/queues/high
GET /api/admin/queues/low

# Queue management
DELETE /api/admin/queues/clear

# Dead letter queue
GET /api/admin/dead-letter-queue
POST /api/admin/dead-letter-queue/{jobId}/retry
```

### Monitoring
```bash
# Health checks
GET /actuator/health

# Metrics
GET /actuator/metrics
GET /actuator/prometheus
```

## 📊 Key Metrics

| Metric | Description |
|--------|-------------|
| `job.execution.time` | Time taken to execute jobs |
| `job.submission.time` | Time taken to submit jobs |
| `simplydone.queue.size` | Current queue sizes |
| `simplydone.jobs.processed` | Total jobs processed |
| `simplydone.jobs.failed` | Total jobs failed |

## ⚙️ Configuration

### Application Properties
```properties
# Server Configuration
server.port=8080

# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379

# Job Scheduler
simplydone.scheduler.rate-limit.requests-per-minute=10
simplydone.scheduler.worker.interval-ms=1000

# Retry Configuration
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

## 🧪 Testing

### Run Tests
```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=ApiTest

# Generate coverage report
mvn jacoco:report
```

### Integration Testing
```bash
# Submit test job
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"message":"Test job","priority":"HIGH","delay":5,"userId":"test"}'

# Check system health
curl http://localhost:8080/api/admin/health

# View statistics
curl http://localhost:8080/api/admin/stats
```

## 🐛 Troubleshooting

### Common Issues

#### Redis Connection Failed
```bash
# Check Redis status
redis-cli ping

# Check Redis logs
docker logs redis
```

#### Port Already in Use
```bash
# Find process using port 8080
netstat -ano | findstr :8080

# Kill process
taskkill /F /PID <PID>
```

#### Job Submission Fails
```bash
# Check rate limit status
curl http://localhost:8080/api/jobs/rate-limit/testuser

# Check application health
curl http://localhost:8080/actuator/health
```

### Performance Issues
```bash
# Check JVM metrics
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Check queue sizes
curl http://localhost:8080/api/admin/stats

# Monitor Redis
redis-cli monitor
```

## 🔧 Development

### Project Structure
```
SimplyDone/
├── src/main/java/com/learnerview/SimplyDone/
│   ├── controller/          # REST controllers
│   ├── service/            # Business logic
│   ├── repository/         # Data access layer
│   ├── model/              # Domain models
│   ├── config/             # Configuration classes
│   └── worker/             # Background workers
├── src/test/               # Test classes
├── src/main/resources/     # Configuration files
└── docs/                   # Documentation
```

### Contributing
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the test suite
6. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Support

- **Documentation**: [Complete Setup Guide](SETUP_GUIDE.md)
- **Issues**: [GitHub Issues](https://github.com/your-repo/simplydone/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-repo/simplydone/discussions)

## 🙏 Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot) - Application framework
- [Redis](https://redis.io/) - In-memory data structure store
- [Micrometer](https://micrometer.io/) - Application metrics
- [Prometheus](https://prometheus.io/) - Monitoring and alerting

---

**SimplyDone** - Making job scheduling simple and reliable 🚀