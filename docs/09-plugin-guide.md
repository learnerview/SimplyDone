# Microservice Plugin Guide

SimplyDone can be deployed as a microservice plugin for integration with existing applications. This guide explains how to configure, deploy, and integrate the plugin with your host application.

## Overview

**Plugin Architecture:**
- Runs as independent containerized microservice
- Exposes REST API for host application integration
- Isolated data storage (separate Redis database, database schema)
- API key authentication for secured communication
- Full observability with metrics and health checks

**Benefits:**
- Decoupled from host application
- Independent scaling and deployment
- Technology-agnostic (host can be any framework)
- Clear service boundaries and contracts
- Fault isolation (plugin failure doesn't crash host)
- Team autonomy (separate codebases)

## Plugin Mode Configuration

Enable plugin mode with environment variables:

```bash
SIMPLYDONE_PLUGIN_MODE=true
PLUGIN_API_KEY=your-secret-api-key-here
REQUIRE_API_KEY=true
```

### Required Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SIMPLYDONE_PLUGIN_MODE` | `false` | Enable plugin mode |
| `PLUGIN_API_KEY` | _(none)_ | API key for host authentication |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/simplydone` | PostgreSQL connection |
| `DATABASE_USER` | `postgres` | Database username |
| `DATABASE_PASSWORD` | _(none)_ | Database password |
| `REDIS_URL` | `redis://localhost:6379` | Redis connection |
| `PORT` | `8080` | HTTP port |

### Optional Configuration

| Variable | Default | Description |
|---|---|---|
| `REDIS_NAMESPACE` | `simplydone-plugin` | Redis key prefix for isolation |
| `REDIS_SEPARATE_DB` | `false` | Use separate Redis database |
| `REDIS_DB_INDEX` | `1` | Redis database index if separate |
| `DB_SCHEMA_PREFIX` | `plugin_` | Database schema prefix |
| `PLUGIN_UPLOAD_DIR` | `/tmp/simplydone-plugin-uploads` | Upload directory |
| `PLUGIN_MAX_QUEUE_SIZE` | `10000` | Maximum queued jobs |
| `PLUGIN_MAX_CONCURRENT_JOBS` | `10` | Concurrent job limit |
| `PLUGIN_JOB_TIMEOUT` | `300` | Job timeout (seconds) |
| `REQUIRE_API_KEY` | `true` | Enforce API key validation |

## Deployment

### Docker Compose (Single Machine)

Deploy plugin alongside host application:

```bash
docker compose -f docker-compose-plugin.yml up -d
```

This starts:
- PostgreSQL (shared database)
- Redis (shared cache, different databases)
- SimplyDone plugin (port 8081)
- Example host application (port 8080)

### Docker Compose (Production)

For production deployment, customize `docker-compose-plugin.yml`:

```yaml
services:
  simplydone-plugin:
    image: your-registry/simplydone:plugin-latest
    ports:
      - "8081:8080"
    environment:
      PLUGIN_API_KEY: ${PLUGIN_API_KEY}   # Use secrets, not hardcoded
      DATABASE_URL: ${DATABASE_URL}
      REDIS_URL: ${REDIS_URL}
```

Deploy:

```bash
export PLUGIN_API_KEY="your-production-api-key"
docker compose -f docker-compose-plugin.yml up -d
```

### Kubernetes

Create `simplydone-plugin-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: simplydone-plugin
spec:
  replicas: 3
  selector:
    matchLabels:
      app: simplydone-plugin
  template:
    metadata:
      labels:
        app: simplydone-plugin
    spec:
      containers:
      - name: simplydone-plugin
        image: your-registry/simplydone:plugin-latest
        ports:
        - containerPort: 8080
        env:
        - name: SIMPLYDONE_PLUGIN_MODE
          value: "true"
        - name: PLUGIN_API_KEY
          valueFrom:
            secretKeyRef:
              name: simplydone-plugin-secret
              key: api-key
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: database-secret
              key: url
        - name: REDIS_URL
          valueFrom:
            secretKeyRef:
              name: redis-secret
              key: url
        livenessProbe:
          httpGet:
            path: /api/plugin/health/detailed
            port: 8080
            httpHeaders:
            - name: X-Plugin-API-Key
              value: ${PLUGIN_API_KEY}
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/plugin/status
            port: 8080
            httpHeaders:
            - name: X-Plugin-API-Key
              value: ${PLUGIN_API_KEY}
          initialDelaySeconds: 30
          periodSeconds: 5
```

Deploy:

```bash
kubectl create secret generic simplydone-plugin-secret \
  --from-literal=api-key='your-api-key'

kubectl apply -f simplydone-plugin-deployment.yaml
```

## API Integration

### Host Application Setup

Configure your host application to use the plugin:

#### 1. Configuration

```properties
# application.properties
simplydone.plugin.enabled=true
simplydone.plugin.endpoint=http://simplydone-plugin:8080
simplydone.plugin.api-key=${PLUGIN_API_KEY}
simplydone.plugin.timeout-seconds=30
simplydone.plugin.retry-attempts=3
```

#### 2. Add Plugin Client

```java
@Service
@RequiredArgsConstructor
public class PluginClient {
    
    @Value("${simplydone.plugin.endpoint}")
    private String pluginEndpoint;
    
    @Value("${simplydone.plugin.api-key}")
    private String apiKey;
    
    private final RestTemplate restTemplate;
    
    /**
     * Submit a job to the plugin.
     */
    public JobSubmissionResponse submitJob(JobRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Plugin-API-Key", apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<JobRequest> entity = new HttpEntity<>(request, headers);
        
        ResponseEntity<JobSubmissionResponse> response = restTemplate.exchange(
            pluginEndpoint + "/api/jobs",
            HttpMethod.POST,
            entity,
            JobSubmissionResponse.class
        );
        
        return response.getBody();
    }
    
    /**
     * Check plugin status before submitting jobs.
     */
    public PluginStatus checkPluginStatus() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Plugin-API-Key", apiKey);
        
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        ResponseEntity<PluginStatus> response = restTemplate.exchange(
            pluginEndpoint + "/api/plugin/status",
            HttpMethod.GET,
            entity,
            PluginStatus.class
        );
        
        return response.getBody();
    }
}
```

#### 3. Use in Host Application

```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final PluginClient pluginClient;
    
    public void sendWelcomeEmail(User user) {
        // Check plugin health first
        PluginStatus status = pluginClient.checkPluginStatus();
        if (!"ACTIVE".equals(status.getStatus())) {
            log.warn("SimpleDone plugin unavailable");
            return;
        }
        
        // Submit email job
        JobRequest emailJob = JobRequest.builder()
            .message("Send welcome email")
            .priority("HIGH")
            .userId(user.getId())
            .jobType("EMAIL_SEND")
            .parameters(Map.of(
                "to", user.getEmail(),
                "subject", "Welcome!",
                "body", "Thank you for signing up."
            ))
            .build();
        
        JobSubmissionResponse response = pluginClient.submitJob(emailJob);
        log.info("Email job submitted: {}", response.getId());
    }
}
```

### Plugin API Endpoints

All endpoints require `X-Plugin-API-Key` header when `REQUIRE_API_KEY=true`.

#### Plugin Status

**GET** `/api/plugin/status`

Returns plugin information and capabilities.

```bash
curl -H "X-Plugin-API-Key: your-api-key" \
  http://simplydone-plugin:8080/api/plugin/status
```

Response:

```json
{
  "plugin": "simplydone-scheduler",
  "version": "1.0.0",
  "status": "ACTIVE",
  "capabilities": [
    "API_CALL",
    "EMAIL_SEND",
    "DATA_PROCESS",
    "FILE_OPERATION",
    "NOTIFICATION",
    "REPORT_GENERATION",
    "CLEANUP"
  ],
  "endpoints": [...],
  "resourceUsage": {
    "cpuUsagePercent": 12.5,
    "memoryUsageMb": 256.0,
    "memoryUsagePercent": 50.0
  },
  "limits": {
    "maxQueueSize": 10000,
    "maxConcurrentJobs": 10,
    "maxJobTimeoutSeconds": 300,
    "memoryLimitMb": 512
  }
}
```

#### Detailed Health

**GET** `/api/plugin/health/detailed`

Comprehensive health information.

```bash
curl -H "X-Plugin-API-Key: your-api-key" \
  http://simplydone-plugin:8080/api/plugin/health/detailed
```

#### Database Health

**GET** `/api/plugin/health/database`

Database connectivity and status.

#### Cache Health

**GET** `/api/plugin/health/cache`

Redis status.

#### Configuration Validation

**POST** `/api/plugin/validate-config`

Validate plugin configuration.

```bash
curl -X POST \
  -H "X-Plugin-API-Key: your-api-key" \
  http://simplydone-plugin:8080/api/plugin/validate-config
```

#### Version/Capabilities

**GET** `/api/plugin/version` - Version and compatibility info

**GET** `/api/plugin/capabilities` - Supported job types and features

**GET** `/api/plugin/metrics` - Performance metrics

**GET** `/api/plugin/config` - Public configuration

#### Plugin Lifecycle

**POST** `/api/plugin/init` - Initialize plugin resources

**POST** `/api/plugin/shutdown` - Graceful shutdown

## Error Handling

Plugin returns enhanced error responses:

```json
{
  "error": "PLUGIN_ERROR",
  "plugin": "simplydone-scheduler",
  "code": "HOST_INTEGRATION_FAILED",
  "message": "Failed to communicate with database",
  "timestamp": "2026-02-19T10:30:00Z",
  "severity": "ERROR",
  "component": "JobExecutor",
  "category": "Database",
  "details": {
    "httpStatus": 503,
    "retryable": true
  },
  "suggestion": "Check database connectivity and try again"
}
```

### Error Codes

| Code | Meaning | Retryable |
|---|---|---|
| `AUTH_FAILED` | API key missing/invalid | No |
| `RESOURCE_LIMIT_EXCEEDED` | Queue or memory full | Yes |
| `DATABASE_ERROR` | Database connectivity | Yes |
| `REDIS_ERROR` | Redis connectivity | Yes |
| `JOB_TIMEOUT` | Job execution timeout | Yes |
| `HOST_INTEGRATION_FAILED` | Can't reach host | Yes |

## Monitoring

### Metrics Endpoint

Access Prometheus metrics:

```bash
curl -H "X-Plugin-API-Key: your-api-key" \
  http://simplydone-plugin:8080/actuator/metrics
```

Key metrics:

- `plugin.availability` - Plugin status (0/1)
- `plugin.jobs.submitted` - Jobs submitted by priority
- `plugin.jobs.executed.success` - Successful jobs
- `plugin.jobs.executed.failed` - Failed jobs
- `plugin.queue.depth` - Jobs in each queue
- `plugin.memory.usage.percent` - Heap memory usage
- `plugin.disk.usage.percent` - Upload directory usage
- `plugin.dependency.*.healthy` - Dependency status (0/1)

### Health Checks for Load Balancers

Use `/api/plugin/status` endpoint with API key:

```yaml
# Kubernetes liveness/readiness
livenessProbe:
  httpGet:
    path: /api/plugin/health/detailed
    port: 8080
    httpHeaders:
    - name: X-Plugin-API-Key
      value: ${PLUGIN_API_KEY}
  initialDelaySeconds: 60
  periodSeconds: 10
```

## Security

### API Key Management

1. **Generate strong API key** (32+ characters):
   ```bash
   openssl rand -base64 32
   ```

2. **Store securely**:
   - Kubernetes: Use Secrets
   - AWS: Use Secrets Manager
   - Docker: Use environment file, not in git

3. **Rotate periodically** (e.g., every 90 days):
   ```bash
   # Update PLUGIN_API_KEY in environment
   # Restart plugin container
   docker compose restart simplydone-plugin
   ```

### Network Security

- Deploy plugin in same VPC/network as host
- Use firewall rules to restrict access
- Plugin port (8081) only accessible to host
- Database and Redis not exposed to public internet

### API Security

- All plugin API endpoints require API key header
- Timing-safe comparison prevents timing attacks
- SSL/TLS recommended for inter-service communication
- Request logging for audit trail

## Resource Isolation

### Database Isolation

Plugin uses schema prefix for table isolation:

- Host tables: `job`, `job_execution`
- Plugin tables: `plugin_job`, `plugin_job_execution`

This allows both to coexist in same database.

### Redis Isolation

Plugin uses separate Redis database:

- Host: Database 0
- Plugin: Database 1 (configurable with `DB_SCHEMA_PREFIX`)

Or separate namespace with key prefix (default: `simplydone-plugin`).

### File Storage Isolation

Uploads stored in separate directory:

```
/uploads/
├── host/          # Host application uploads
└── plugin/        # Plugin uploads (auto-cleanup)
```

## Troubleshooting

### Plugin not responding

```bash
# Check container status
docker ps | grep simplydone-plugin

# Check logs
docker logs simplydone-plugin

# Test API key
curl -H "X-Plugin-API-Key: your-api-key" \
  http://localhost:8081/api/plugin/status

# Check health without API key (status endpoint is public)
curl http://localhost:8081/api/plugin/status
```

### Database connection errors

```bash
# Verify database is running
docker exec simplydone-plugin-postgres pg_isready -U postgres

# Check connection URL
echo $DATABASE_URL

# Test directly
psql postgresql://postgres:postgres@localhost:5433/simplydone
```

### Redis connection errors

```bash
# Verify Redis is running
docker exec simplydone-plugin-redis redis-cli ping

# Check Redis URL
echo $REDIS_URL

# Test directly
redis-cli -h localhost -p 6380 ping
```

### Jobs not executing

- Check plugin health: `/api/plugin/health/detailed`
- View queue depths: `/api/plugin/status`
- Check dead-letter queue: `/api/jobs` with admin endpoint
- Review logs: `docker logs simplydone-plugin`

## Advanced Configuration

### Multi-Instance Plugin

For high availability, run multiple plugin instances:

```yaml
services:
  simplydone-plugin-1:
    image: simplydone:plugin-latest
    ports:
      - "8081:8080"
    environment:
      PLUGIN_API_KEY: ${PLUGIN_API_KEY}

  simplydone-plugin-2:
    image: simplydone:plugin-latest
    ports:
      - "8082:8080"
    environment:
      PLUGIN_API_KEY: ${PLUGIN_API_KEY}

  # Load balancer
  nginx:
    image: nginx:latest
    ports:
      - "8080:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
```

### Custom Job Types

To add custom job types, extend the plugin with a strategy implementation (in separate repository).

## Migration from Standalone

If migrating from standalone to plugin mode:

1. **Backup data**:
   ```bash
   pg_dump postgresql://postgres:postgres@localhost:5433/simplydone > backup.sql
   ```

2. **Enable plugin mode**:
   ```bash
   export SIMPLYDONE_PLUGIN_MODE=true
   export PLUGIN_API_KEY=new-api-key
   docker compose restart simplydone-plugin
   ```

3. **Update host application** to use plugin client

4. **Verify integration** with test submissions

5. **Monitor** for any issues

## Support

For issues or questions:
1. Check logs: `docker logs simplydone-plugin`
2. Review health endpoint: `/api/plugin/health/detailed`
3. Verify configuration in `/api/plugin/config`
4. Check troubleshooting section above
