# Troubleshooting Guide

Common issues and solutions for SimplyDone.

## Startup issues

### Application fails to start

**Symptoms:** Maven exits with error, or application crashes immediately.

**Common causes:**

1. **Port 8080 already in use**
   ```bash
   # Windows
   netstat -ano | findstr :8080
   
   # macOS/Linux
   lsof -i :8080
   ```
   Solution: Kill process or use different port
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
   ```

2. **Docker containers not running**
   ```bash
   docker ps
   ```
   If PostgreSQL or Redis missing:
   ```bash
   docker compose up -d
   docker ps  # Verify both are UP
   ```

3. **Maven dependency download failure**
   ```bash
   mvn clean install -X  # See detailed output
   ```
   Solution: Clear cache and retry
   ```bash
   rm -rf ~/.m2/repository
   mvn clean install
   ```

4. **Java version mismatch**
   ```bash
   java -version  # Must be 17+
   ```
   Solution: Install Java 17 or later

5. **Property file syntax error**
   ```bash
   # Error: Invalid properties file syntax
   ```
   Check `application.properties` for:
   - Unclosed quotes
   - Invalid characters before `=`
   - Tabs instead of spaces (properties use spaces)

**Check logs:**
```bash
tail -f logs_debug.txt
```

Look for:
- `Connection refused` — Database or Redis not running
- `No DataSource` — Database configuration missing
- `No Spring Boot application` — Main class not found

---

## Database connection issues

### "Connection refused" error

**Symptoms:** Application starts but immediately fails when accessing data.

**Root cause:** PostgreSQL container not running or wrong port.

**Solution:**
```bash
# Check container health
docker compose ps

# If PostgreSQL not running
docker compose up -d postgres

# Test connection
docker exec simplydone-postgres psql -U postgres -c "SELECT 1"

# View logs
docker compose logs postgres
```

### "Can't find simplydone database"

**Log shows:**
```
ERROR: database "simplydone" does not exist
```

**Solution:**
```bash
# Create database
docker exec simplydone-postgres createdb -U postgres simplydone

# Or drop and recreate containers
docker compose down -v
docker compose up -d
```

### "FATAL: Ident authentication failed"

**Cause:** PostgreSQL authentication method incompatible.

**Solution:**
```bash
# Verify credentials in connection string
# Should be: jdbc:postgresql://localhost:5433/simplydone

# Recreate containers with correct credentials
docker compose down -v
docker compose up -d
```

### Slow database queries

**Symptoms:** Dashboard takes 5+ seconds to load, timeout errors.

**Check query performance:**
```bash
# Connect to database
docker exec -it simplydone-postgres psql -U postgres -d simplydone

# Enable query logging
ALTER SYSTEM SET log_min_duration_statement = 1000;
SELECT pg_reload_conf();

# Check slow queries
SELECT query, mean_exec_time FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;

# Check table sizes
SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) 
FROM pg_tables ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

**Solution:**
- Add indexes on frequently queried columns
- Archive old job records
- Increase database server resources (CPU, RAM)

---

## Redis connection issues

### "Could not get a resource from the pool" error

**Cause:** Redis connection pool exhausted.

**Solutions:**
```properties
# Increase pool size in application.properties
spring.data.redis.jedis.pool.max-active=20
spring.data.redis.jedis.pool.max-idle=10
```

Or diagnose usage:
```bash
docker exec simplydone-redis redis-cli info clients
# maxclients=10000
```

### Redis data gone after restart

**Cause:** Persistence not enabled (data in memory only).

**Solution:** Add to docker-compose.yml
```yaml
redis:
  command: redis-server --appendonly yes
```

Then:
```bash
docker compose down
docker compose up -d
```

### "MOVED" or "ASK" errors with Redis Cluster

**Cause:** Connecting to single node in cluster mode.

**Solution:** Use non-cluster Redis or fix connection string.

---

## Job processing issues

### Jobs stuck in queue

**Symptom:** Jobs submitted but never execute, stay in HIGH/LOW queue.

**Check worker thread:**
```bash
curl http://localhost:8080/actuator/health
# Should show UP status
```

**Check worker status:**
Visit Admin page at `http://localhost:8080/admin`, check "Worker Status".

**Likely causes:**

1. **Worker thread crashed**
   ```bash
   # Check logs
   tail -f logs_debug.txt | grep -i worker
   ```
   Solution: Restart application
   ```bash
   # Kill mvn
   Ctrl+C
   
   # Start again
   mvn spring-boot:run
   ```

2. **Worker polling interval too high**
   ```properties
   # Current setting
   simplydone.scheduler.worker.interval-ms=1000
   
   # Change to more responsive
   simplydone.scheduler.worker.interval-ms=100
   ```

3. **Redis queue corrupted**
   ```bash
   docker exec simplydone-redis redis-cli
   > LLEN simplydone:jobs:high
   > LRANGE simplydone:jobs:high 0 -1
   ```
   Solution: Clear queue from admin page or:
   ```bash
   docker exec simplydone-redis redis-cli DEL simplydone:jobs:high simplydone:jobs:low
   ```

### Jobs keep failing and retrying

**Symptom:** Job executes, fails, retries, fails again repeatedly.

**Check dead-letter queue:**
```bash
# Via API
curl http://localhost:8080/api/admin/dead-letter-queue

# Via Redis
docker exec simplydone-redis redis-cli LRANGE dead_letter:jobs 0 -1
```

**Get job details:**
```bash
curl http://localhost:8080/api/jobs/job-<id>
# Look for error_message field
```

**Common job type errors:**

**API_CALL**
- Network timeout → Increase timeout in job parameters
- 4xx error → Fix URL or authentication in parameters
- Rate limited → Add delay between requests or use LOW priority

**EMAIL_SEND**
- Authentication failed → Verify EMAIL_API_KEY
- Invalid address → Check email format
- Disabled → EMAIL_ENABLED=true required

**FILE_OPERATION**
- File not found → Verify absolute path exists
- Permission denied → Check file permissions
- Disk full → Free disk space

**DATA_PROCESS**
- CSV malformed → Validate CSV syntax
- Column not found → Check column names
- Out of memory → Split large files

**NOTIFICATION**
- Invalid webhook URL → Test with curl first
- 4xx/5xx response → Webhook server error
- Timeout → Webhook server too slow

**REPORT_GENERATION**
- Output path invalid → Verify directory exists
- Disk full → Free space or change output directory

**CLEANUP**
- Directory not found → Verify path
- Permission denied → Check directory permissions

### Jobs execute successfully but no output

**Symptom:** Job shows EXECUTED status but result field empty.

**Check job details:**
```bash
curl http://localhost:8080/api/jobs/job-<id> | jq .job.output
```

**Likely issues:**
1. Job type doesn't produce output (NOTIFICATION, EMAIL_SEND)
2. Output file created but not captured
3. Strategy didn't return result

**Solution:** Check job type documentation for what output to expect.

---

## File upload issues

### "File upload directory does not exist" error

**Cause:** Upload directory path invalid or doesn't exist.

**Solution:**
```properties
# application-prod.properties should use
simplydone.upload.directory=${UPLOAD_DIR:${java.io.tmpdir}/simplydone-uploads}
```

This auto-creates on first use. If still errors:
```bash
# Manually create directory
# Windows
mkdir C:\Users\<user>\AppData\Local\Temp\simplydone-uploads

# Linux
mkdir -p /tmp/simplydone-uploads

# macOS
mkdir -p /var/folders/.../T/simplydone-uploads
```

### Upload fails with 500 error

**Check logs for error:**
```bash
tail -f logs_debug.txt | grep -i upload
```

**Likely causes:**

1. **File size exceeds limit** (50MB default)
   ```properties
   simplydone.upload.max-size-mb=100
   ```

2. **Disk full**
   ```bash
   df -h  # Check available space
   ```

3. **Bad filename with special characters**
   - Solution: Use alphanumeric filenames
   - Uploaded files renamed to safe names anyway

### Files not auto-cleaning up

**Cause:** Cleanup scheduler disabled or interval too long.

**Check configuration:**
```properties
# Check in application.properties
simplydone.upload.retention-minutes=10
```

**Manually trigger cleanup:**
```bash
# Via API
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "CLEANUP",
    "priority": "LOW",
    "userId": "admin",
    "message": "Manual cleanup",
    "parameters": {
      "operation": "DELETE_OLD_FILES",
      "directory": "<upload-dir>",
      "olderThanDays": 1
    }
  }'
```

---

## Performance issues

### Application slow or unresponsive

**Symptoms:** Requests take 5+ seconds, timeouts.

**Check metrics:**
```bash
curl http://localhost:8080/actuator/metrics | jq .names
# Key metrics: jvm.memory.used, http.server.requests
```

**Check specific metrics:**
```bash
curl 'http://localhost:8080/actuator/metrics/http.server.requests'
```

**Common causes:**

1. **High memory usage**
   ```bash
   curl http://localhost:8080/actuator/metrics/jvm.memory.used
   # If >512MB, heap too full
   ```
   Solution:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xmx1G"
   ```

2. **Database connection pooled**
   ```bash
   docker exec simplydone-postgres psql -U postgres -d simplydone -c "SELECT count(*) FROM pg_stat_activity;"
   # Should be <10
   ```
   Solution: Increase connection pool size
   ```properties
   spring.datasource.hikari.maximum-pool-size=20
   ```

3. **Redis slow
   ```bash
   docker exec simplydone-redis redis-cli --stat
   # Watch ops/sec and latency
   ```
   Solution:
   - Reduce queue size (archive old jobs)
   - Increase Redis memory
   - Use Redis Cluster for scale

4. **Many jobs in queue**
   ```bash
   curl http://localhost:8080/api/admin/queues/high | jq '.length'
   ```
   Solution:
   - Increase worker thread frequency
   - Add more instances (horizontally scale)
   - Process jobs in batches

### Disk usage keeps growing

**Check what's consuming space:**
```bash
du -sh *  # Current directory
du -sh /path/to/uploads  # Upload directory
```

**Solutions:**

1. **Upload files not cleaning up**
   - Verify FileCleanupScheduler running
   - Check retention time setting
   - Manually delete old files

2. **Log files growing too large**
   - Check `logs_debug.txt` file size
   - Enable log rotation in application.properties
   - Archive old logs

3. **Database growing**
   ```bash
   docker exec simplydone-postgres psql -U postgres -d simplydone -c "\l+"
   ```
   - Archive old job records to cold storage
   - Implement retention policy

---

## Deployment issues

### Render deployment stuck

**Symptom:** Deployment shows "Building" for over 10 minutes.

**Check logs:**
- Click "Logs" tab in Render dashboard
- Look for build errors (Maven, Docker, Java)

**Common causes:**

1. **Maven dependency download slow**
   - First build can take 5-10 minutes
   - Subsequent builds are faster (cached)

2. **Docker build fails**
   - Check Dockerfile is present
   - Verify Java 17 compatibility
   - Check free disk in build container

3. **Health check timeout**
   - Application takes >60 seconds to start
   - Database migrations slow
   - Render kills container as unhealthy
   Solution: Increase health check timeout in render.yaml

### Application crashes after deployment

**Check logs in Render dashboard:**

**Common crashes:**

1. **Cannot connect to database**
   ```
   ERROR: Connection refused
   ```
   Solution: Verify DATABASE_URL environment variable set

2. **Cannot connect to Redis**
   ```
   ERROR: Cannot get a resource from the pool
   ```
   Solution: Verify REDIS_URL environment variable set

3. **Out of memory**
   ```
   java.lang.OutOfMemoryError: Java heap space
   ```
   Solution: Increase memory in Render instance type

4. **File system issues**
   ```
   java.nio.file.FileSystemException
   ```
   Solution: Use `/tmp` or mapped volume for uploads

### Render dashboard not loading

**Cause:** Static assets (CSS, JS) not served correctly.

**Solution:**
- Check docker-compose logs: `docker compose logs app`
- Verify `/static` directory copied to image
- Check Dockerfile `COPY` commands

---

## Testing and validation

### Run health checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Database connectivity
docker exec simplydone-postgres pg_isready -U postgres

# Redis connectivity  
docker exec simplydone-redis redis-cli ping
```

### Run test job submission

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Test job",
    "priority": "HIGH",
    "userId": "test-user",
    "jobType": "API_CALL",
    "parameters": {
      "url": "https://httpbin.org/get",
      "method": "GET"
    }
  }'
```

### Run comprehensive test suite

```powershell
# PowerShell
.\scripts\test-comprehensive.ps1 -BaseUrl "http://localhost:8080"

# Should show: 35/35 PASSED
```

---

## Getting help

### Check documentation

1. [Getting Started Guide](01-getting-started.md)
2. [Configuration Reference](05-configuration.md)
3. [API Reference](03-api-reference.md)
4. [Architecture Guide](07-architecture.md)

### Enable debug logging

Add to `application.properties`:
```properties
logging.level.root=DEBUG
logging.level.com.learnerview.SimplyDone=TRACE
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

Then restart application and check `logs_debug.txt` for detailed information.

### Contact support

For unresolved issues:
1. Collect logs and error messages
2. Document steps to reproduce
3. Include environment details (OS, Java version, container versions)
4. Check GitHub issues for similar problems
5. Submit detailed bug report with minimal reproduction case
