# System Configuration

SimplyDone is configured through `src/main/resources/application.properties`. All sensitive values are read from environment variables with safe defaults for local development.

---

## Environment Variables

These are the variables you should set in production (Render, Docker, etc.).

| Environment Variable | Maps to Property | Default | Purpose |
|---|---|---|---|
| `PORT` | `server.port` | `8080` | HTTP listening port |
| `REDIS_URL` | Parsed by `entrypoint.sh` and `RedisUrlEnvironmentPostProcessor` | `redis://localhost:6379` | Redis/Valkey connection URL. Supported formats: `redis://host:port`, `redis://:password@host:port`, `rediss://host:port` (TLS). |
| `SPRING_DATA_REDIS_HOST` | `spring.data.redis.host` | `localhost` | Direct Redis host override (set by entrypoint.sh from REDIS_URL). |
| `SPRING_DATA_REDIS_PORT` | `spring.data.redis.port` | `6379` | Direct Redis port override. |
| `SPRING_DATA_REDIS_PASSWORD` | `spring.data.redis.password` | (empty) | Direct Redis password override. |
| `SPRING_DATA_REDIS_SSL_ENABLED` | `spring.data.redis.ssl.enabled` | `false` | Set to `true` when using TLS (`rediss://`). Set automatically from `REDIS_URL`. |
| `DATABASE_URL` | Parsed by `entrypoint.sh` | H2 in-memory | PostgreSQL connection in Render format: `postgresql://USER:PASS@HOST:PORT/DBNAME` |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | H2 in-memory | Direct JDBC URL override (alternative to `DATABASE_URL`) |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | `sa` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | (empty) | Database password |
| `SMTP_USERNAME` | `spring.mail.username` | (empty) | Gmail address for email jobs |
| `SMTP_PASSWORD` | `spring.mail.password` | (empty) | Gmail App Password |
| `EMAIL_ENABLED` | `simplydone.email.enabled` | `false` | Set to `true` to enable email sending |
| `UPLOAD_DIR` | `simplydone.upload.directory` | system temp dir | Directory for uploaded files |

Note: When `DATABASE_URL` is not set and `SPRING_DATASOURCE_URL` is also unset, the application uses an H2 in-memory database. This is suitable for development and testing but data is lost on restart.

---

## Redis Configuration

The application reads Redis connection details from individual Spring properties. On Render (and in Docker), `entrypoint.sh` parses `REDIS_URL` and exports the values. A Java-side `RedisUrlEnvironmentPostProcessor` provides a fallback for non-Docker environments.

| Property | Default | Description |
|---|---|---|
| `spring.data.redis.host` | `localhost` | Redis/Valkey hostname |
| `spring.data.redis.port` | `6379` | Redis/Valkey port (standard) |
| `spring.data.redis.password` | (empty) | Redis AUTH password (set from `REDIS_URL` if present) |
| `spring.data.redis.ssl.enabled` | `false` | Enable TLS; set automatically when `REDIS_URL` uses `rediss://` |
| `spring.data.redis.timeout` | `2000ms` | Connection and command timeout |
| `spring.data.redis.lettuce.pool.max-active` | `8` | Maximum pool connections (requires `commons-pool2` on classpath) |
| `spring.data.redis.lettuce.pool.max-idle` | `8` | Maximum idle connections in pool |
| `spring.data.redis.lettuce.pool.min-idle` | `0` | Minimum idle connections in pool |

> **Note for local Docker Compose:** The `docker-compose.yml` maps the Redis container's internal port 6379 to host port 6380 to avoid conflicts with a locally-running Redis. The `REDIS_URL` is set to `redis://redis:6379` (internal container-to-container) inside Docker. When running the app directly with Maven, use `REDIS_URL=redis://localhost:6380` or the host/port env vars directly.

---

## Queue Configuration

| Property | Default | Description |
|---|---|---|
| `simplydone.scheduler.queues.high` | `jobs:high` | Redis key for the HIGH priority sorted set |
| `simplydone.scheduler.queues.low` | `jobs:low` | Redis key for the LOW priority sorted set |
| `simplydone.scheduler.redis.key-prefix` | `simplydone` | Prefix for all Redis keys (useful in shared Redis environments) |

---

## Rate Limiting

| Property | Default | Description |
|---|---|---|
| `simplydone.scheduler.rate-limit.requests-per-minute` | `60` | Maximum job submissions per user per minute |

---

## Worker Configuration

| Property | Default | Description |
|---|---|---|
| `simplydone.scheduler.worker.interval-ms` | `1000` | Polling interval in milliseconds. Lower values reduce latency but increase Redis load |
| `simplydone.scheduler.worker.max-jobs-per-cycle` | `5` | Maximum jobs drained per polling tick. Increase for higher throughput under load |

---

## Retry Configuration

| Property | Default | Description |
|---|---|---|
| `simplydone.retry.max-attempts` | `3` | Maximum retry attempts before a job is moved to the DLQ |
| `simplydone.retry.initial-delay-seconds` | `5` | Delay before the first retry |
| `simplydone.retry.backoff-multiplier` | `2.0` | Multiplier applied on each retry (5s, 10s, 20s) |

---

## Database Connection Pool (HikariCP)

| Property | Default | Description |
|---|---|---|
| `spring.datasource.hikari.maximum-pool-size` | `5` | Maximum connections. Increase for high-throughput worker deployments |
| `spring.datasource.hikari.connection-timeout` | `20000` | Milliseconds to wait for a connection before throwing |
| `spring.datasource.hikari.idle-timeout` | `300000` | Milliseconds before an idle connection is evicted |

---

## Email (SMTP)

SimplyDone uses Gmail SMTP via Spring Mail (JavaMailSender). The email service is disabled by default and must be explicitly enabled.

| Property | Default | Description |
|---|---|---|
| `simplydone.email.enabled` | `false` | Master switch for email dispatch |
| `spring.mail.host` | `smtp.gmail.com` | SMTP server host |
| `spring.mail.port` | `587` | SMTP port (STARTTLS) |
| `spring.mail.username` | (from env) | Gmail address |
| `spring.mail.password` | (from env) | Gmail App Password — generate at myaccount.google.com/apppasswords |

---

## File Upload

| Property | Default | Description |
|---|---|---|
| `simplydone.upload.directory` | system temp directory | Where uploaded files are stored |
| `simplydone.upload.max-file-size` | `52428800` (50 MB) | Maximum size of a single uploaded file |
| `simplydone.upload.retention-minutes` | `10` | How long uploaded files are kept before cleanup |

---

## Spring Actuator

| Property | Default | Description |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` | Exposed actuator endpoints |
| `management.endpoint.health.show-details` | `when-authorized` | Change to `always` for full component health details |
| `management.health.mail.enabled` | mirrors `EMAIL_ENABLED` | Controls whether the mail health indicator is active |

