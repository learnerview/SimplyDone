# Deployment and Operations Guide

This guide covers deploying SimplyDone to production, operational housekeeping, and scaling strategies.

---

## Deployment Platforms

### Render (Recommended)

SimplyDone includes a `render.yaml` Blueprint. To deploy:

1. Sign in to [render.com](https://render.com) and click **New** > **Blueprint**.
2. Connect the `learnerview/SimplyDone` GitHub repository.
3. Render provisions the web service, a managed PostgreSQL database, and a managed Redis instance automatically.
4. Set the following environment variables in the `simplydone-secrets` group in Render:
   - `SMTP_USERNAME` — Gmail address for email job sending
   - `SMTP_PASSWORD` — Gmail App Password
   - `EMAIL_ENABLED` — `true` to enable email

Render injects `DATABASE_URL` and `REDIS_URL` automatically. The `entrypoint.sh` script parses the Render `DATABASE_URL` format (`postgresql://USER:PASS@HOST:PORT/DBNAME`) into the JDBC variables that Spring requires.

---

### Docker Compose (Self-Hosted or Local)

`docker-compose.yml` runs the full stack: application, PostgreSQL 15, and Redis 7.

```bash
# Clone the repository
git clone https://github.com/learnerview/SimplyDone.git
cd SimplyDone

# Copy the environment template
cp .env.template .env
# Edit .env to set SMTP credentials if email is needed

# Start all services
docker compose up -d

# Verify all containers are running
docker compose ps

# Follow application logs
docker compose logs -f app
```

Services are accessible at:
- Application: `http://localhost:8080`
- PostgreSQL: `localhost:5433`
- Redis: `localhost:6380`

To stop and remove all containers:

```bash
docker compose down
```

To also remove the PostgreSQL data volume:

```bash
docker compose down -v
```

---

### Docker (Single Container)

If you want to run only the application container and supply your own PostgreSQL and Redis:

```bash
docker build -t simplydone .
docker run -p 8080:8080 \
  -e DATABASE_URL=postgresql://user:pass@host:5432/simplydone \
  -e REDIS_URL=redis://host:6379 \
  -e EMAIL_ENABLED=false \
  simplydone
```

---

## Production Checklist

### Security

- Set `DATABASE_URL` and `REDIS_URL` exclusively through environment variables. Never commit credentials to source control.
- Change the default PostgreSQL password (`postgres`) before production use.
- Enable TLS on the Redis connection if your Redis provider supports it (prefix the URL with `rediss://`).
- Set `management.endpoint.health.show-details=when-authorized` (already the default) to avoid exposing health details publicly.

### Monitoring

SimplyDone exposes `/actuator/prometheus` for Prometheus scraping. A minimal Grafana setup can display:
- `jvm.memory.used` for heap pressure
- `http.server.requests` for request throughput and latency
- Custom counters: query `stats:executed` and `stats:rejected` in Redis for job throughput

### Queue Maintenance

Periodically audit the Dead Letter Queue (DLQ) via the Admin interface at `/admin`. Jobs in the DLQ have exhausted their retry budget and require manual attention before they can be re-enqueued.

---

## Scaling

### Horizontal Scaling

Multiple application instances can run against the same PostgreSQL database and Redis cluster. The worker uses Redis `WATCH + MULTI/EXEC` to ensure each job is picked up by exactly one instance. No additional distributed coordination is required.

Steps:
1. Deploy additional application instances (Render automatically does this if you increase the instance count).
2. Ensure all instances share the same `DATABASE_URL` and `REDIS_URL`.

### Vertical Scaling

- Increase `spring.datasource.hikari.maximum-pool-size` if the database is the bottleneck.
- Decrease `simplydone.scheduler.worker.interval-ms` to reduce job pickup latency (minimum: 100 ms).
- Increase `-Xmx` JVM heap if processing large data sets in `DATA_PROCESS` or `REPORT_GENERATION` jobs.

---

## Disaster Recovery

### Database Backups

Schedule daily PostgreSQL dumps using `pg_dump`:

```bash
pg_dump -h HOST -U postgres simplydone > backup-$(date +%F).sql
```

Render's managed PostgreSQL includes automated daily backups with a 7-day retention window.

### Redis Persistence

Redis data (queues, rate limiting counters) is ephemeral by design. Jobs that were in the queue but not yet persisted to PostgreSQL will be lost if Redis restarts without persistence enabled. To enable Redis persistence:
- **RDB**: periodic snapshots — acceptable for most deployments
- **AOF**: append-only file — lower risk of data loss, higher write overhead

When using Render's managed Redis, snapshots are enabled by default.

---

## Log Management

Application logs are written to standard output in the format:

```
2024-01-15 10:30:00 [main] INFO  c.l.S.SimplyDoneApplication - Started SimplyDoneApplication
```

In a Docker environment, collect logs with:

```bash
docker compose logs -f app
```

On Render, logs are available in the web dashboard and via the Render CLI (`render logs`).

Log level is controlled by `logging.level.com.learnerview.SimplyDone` in `application.properties`. The default is `INFO`. Change to `DEBUG` for detailed strategy execution tracing.

