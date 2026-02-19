# Configuration

All configuration is managed through `src/main/resources/application.properties`. Environment-specific overrides go in `application-{profile}.properties`. Environment variables can substitute any property using the `${VAR_NAME:default}` syntax.

---

## Server

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | HTTP port. Set with `PORT` environment variable. |
| `spring.application.name` | `SimplyDone` | Application name used in logs and actuator info. |

---

## Redis

| Property | Default | Description |
|---|---|---|
| `spring.data.redis.url` | `redis://localhost:6379` | Redis connection URL. Set with `REDIS_URL` environment variable. Supports `redis://` and `rediss://` (TLS). |

---

## PostgreSQL

| Property | Default | Description |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/simplydone` | JDBC connection URL. Set with `DATABASE_URL` environment variable. |
| `spring.datasource.username` | `postgres` | Database user. Set with `DATABASE_USER` environment variable. |
| `spring.datasource.password` | `123456` | Database password. Set with `DATABASE_PASSWORD` environment variable. |
| `spring.datasource.driver-class-name` | `org.postgresql.Driver` | JDBC driver class. Do not change. |

### Connection pool (HikariCP)

| Property | Default | Description |
|---|---|---|
| `spring.datasource.hikari.connection-timeout` | `20000` | Maximum milliseconds to wait for a connection (20 seconds). |
| `spring.datasource.hikari.maximum-pool-size` | `10` | Maximum number of connections in the pool. |
| `spring.datasource.hikari.minimum-idle` | `5` | Minimum number of idle connections maintained. |
| `spring.datasource.hikari.idle-timeout` | `300000` | Maximum milliseconds a connection can sit idle before being removed (5 minutes). |
| `spring.datasource.hikari.max-lifetime` | `1200000` | Maximum lifetime of a connection in the pool (20 minutes). |

---

## JPA / Hibernate

| Property | Default | Description |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema management strategy. `update` applies incremental changes. Use `validate` in production to prevent automatic schema modifications. |
| `spring.jpa.show-sql` | `false` | Print generated SQL statements to the log. |
| `spring.jpa.properties.hibernate.format_sql` | `true` | Format SQL output when `show-sql` is enabled. |
| `spring.jpa.defer-datasource-initialization` | `true` | Ensures Hibernate schema creation runs before any data initializer scripts. |

---

## Email

| Property | Default | Description |
|---|---|---|
| `simplydone.email.enabled` | `false` | Enable email delivery via Resend. When `false`, EMAIL_SEND jobs are accepted and queued but not sent. Set with `EMAIL_ENABLED` environment variable. |
| `simplydone.email.api-key` | _(empty)_ | Resend API key. Set with `EMAIL_API_KEY` environment variable. Required when email is enabled. |
| `simplydone.email.from-address` | `noreply@example.com` | Sender address for all outgoing emails. Must be a verified domain in your Resend account. Set with `EMAIL_FROM_ADDRESS` environment variable. |

---

## Scheduler

These properties are grouped under the `simplydone.scheduler` prefix.

### API endpoints

| Property | Default | Description |
|---|---|---|
| `simplydone.scheduler.api.enabled` | `true` | Enable the `/api/jobs` REST endpoints. |
| `simplydone.scheduler.api.admin-endpoints` | `true` | Enable the `/api/admin` REST endpoints. |
| `simplydone.scheduler.api.view-endpoints` | `false` | Enable the Thymeleaf web UI routes. Must be `true` for the browser interface to work. |

### Rate limiting

| Property | Default | Description |
|---|---|---|
| `simplydone.scheduler.rate-limit.requests-per-minute` | `10` | Maximum job submissions per user per minute. Submissions beyond this limit return HTTP 429. Minimum value is 1. |

### Worker

| Property | Default | Description |
|---|---|---|
| `simplydone.scheduler.worker.interval-ms` | `1000` | How often the background worker polls the queues, in milliseconds. Minimum value is 100. Lower values reduce latency but increase CPU and Redis load. |

### Redis keys

| Property | Default | Description |
|---|---|---|
| `simplydone.scheduler.redis.key-prefix` | `simplydone` | Prefix applied to all Redis keys created by the scheduler. Change this if running multiple instances in the same Redis instance. |
| `simplydone.scheduler.queues.high` | `jobs:high` | Redis list key for the HIGH priority queue. |
| `simplydone.scheduler.queues.low` | `jobs:low` | Redis list key for the LOW priority queue. |
| `simplydone.scheduler.queues.dead-letter` | `dead_letter:jobs` | Redis list key for jobs that exhausted all retry attempts. |
| `simplydone.scheduler.stats.executed` | `stats:executed` | Redis counter key for total successfully executed jobs. |
| `simplydone.scheduler.stats.rejected` | `stats:rejected` | Redis counter key for total rate-limited job submissions. |

---

## Retry

| Property | Default | Description |
|---|---|---|
| `simplydone.retry.max-attempts` | `3` | Maximum number of execution attempts for a failing job before it is moved to the dead-letter queue. |
| `simplydone.retry.backoff-multiplier` | `2.0` | Multiplier applied to the delay between successive retry attempts (exponential backoff). |
| `simplydone.retry.initial-delay-seconds` | `5` | Delay before the first retry, in seconds. Subsequent retries multiply this by the backoff multiplier: 5s, 10s, 20s with the defaults. |

---

## Feature flags

| Property | Default | Description |
|---|---|---|
| `simplydone.enhanced-executor` | `true` | Enable the enhanced `/api/v2/jobs` endpoints with extended scheduling options. |
| `simplydone.enhanced-rate-limiting` | `true` | Enable the enhanced rate limiting service. |
| `simplydone.enhanced-retry` | `true` | Enable the enhanced retry service. |

---

## Actuator and monitoring

| Property | Default | Description |
|---|---|---|
| `management.endpoints.web.exposure.include` | `health,info,metrics,prometheus` | Actuator endpoints exposed over HTTP. |
| `management.endpoint.health.show-details` | `always` | Show detailed health information including Redis and PostgreSQL component status. |

---

## System metadata

These are informational properties used in the admin API and health responses.

| Property | Default | Description |
|---|---|---|
| `simplydone.system.name` | `SimplyDone Priority Job Scheduler` | Display name of the system. |
| `simplydone.system.version` | `1.0.0` | Application version string. |
| `simplydone.system.description` | `Professional Priority Job Scheduling System with Redis & PostgreSQL` | Short description. |

---

## Environment variable reference

For deployment environments where file-based configuration is not practical, the following environment variables are read directly:

| Environment Variable | Property | Description |
|---|---|---|
| `PORT` | `server.port` | HTTP listen port |
| `REDIS_URL` | `spring.data.redis.url` | Redis connection URL |
| `DATABASE_URL` | `spring.datasource.url` | PostgreSQL JDBC URL |
| `DATABASE_USER` | `spring.datasource.username` | PostgreSQL username |
| `DATABASE_PASSWORD` | `spring.datasource.password` | PostgreSQL password |
| `EMAIL_ENABLED` | `simplydone.email.enabled` | Set to `true` to enable email delivery |
| `EMAIL_API_KEY` | `simplydone.email.api-key` | Resend API key |
| `EMAIL_FROM_ADDRESS` | `simplydone.email.from-address` | Sender email address |

An `.env.example` file is included in the repository root showing all supported variables.

---

## Example: minimal production configuration

```properties
server.port=${PORT:8080}

spring.data.redis.url=${REDIS_URL}
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate

simplydone.email.enabled=${EMAIL_ENABLED:false}
simplydone.email.api-key=${EMAIL_API_KEY:}
simplydone.email.from-address=${EMAIL_FROM_ADDRESS:noreply@example.com}

simplydone.scheduler.api.enabled=true
simplydone.scheduler.api.admin-endpoints=true
simplydone.scheduler.api.view-endpoints=true

simplydone.retry.max-attempts=3
simplydone.retry.backoff-multiplier=2.0
simplydone.retry.initial-delay-seconds=5

management.endpoints.web.exposure.include=health,metrics,prometheus
management.endpoint.health.show-details=never
```
