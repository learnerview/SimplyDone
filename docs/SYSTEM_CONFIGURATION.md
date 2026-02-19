# System Configuration

Configuration in SimplyDone is managed through a hierarchical system of property files and environment variables, ensuring flexibility across development and production environments.

## Core Configuration File
The primary configuration resides in `src/main/resources/application.properties`. This file defines the default system behavior.

## Environment Variable Overrides
In cloud and containerized environments (Docker, Render), environment variables should be used to override default settings.

| Property | Environment Variable | Default | Purpose |
|---|---|---|---|
| `server.port` | `PORT` | `8080` | HTTP listening port |
| `spring.data.redis.url` | `REDIS_URL` | `redis://localhost:6379` | Redis connection string |
| `spring.datasource.url` | `DATABASE_URL` | `jdbc:postgresql://...` | PostgreSQL JDBC URL |
| `simplydone.email.enabled` | `EMAIL_ENABLED` | `false` | Enable/Disable SMTP sending |
| `simplydone.email.api-key` | `EMAIL_API_KEY` | _(none)_ | SMTP Provider API Key |

## Persistence Layer (PostgreSQL)
Configuration for the long-term storage of job records and audit logs.
- **Connection Timeout**: 20,000ms
- **Maximum Pool Size**: 10 (Recommended to scale based on worker threads)
- **DDL Auto**: `update` for development; `validate` for production.

## Messaging Layer (Redis)
Configuration for high-throughput queuing.
- **Key Prefix**: `simplydone` (Ensures isolation in shared Redis environments)
- **Queue Lanes**: `jobs:high`, `jobs:low`
- **DLQ**: `dead_letter:jobs`

## Execution Engine Tuning
- **Polling Interval**: `simplydone.scheduler.worker.interval-ms` (Default: 1000ms). Lower values reduce latency but increase infrastructure load.
- **Retry Strategy**: 
  - `max-attempts`: 3
  - `backoff-multiplier`: 2.0
  - `initial-delay-seconds`: 5

## Performance Monitoring
SimplyDone utilizes Spring Actuator for system observability.
- **Exposure**: `health`, `metrics`, `prometheus`.
- **Health Details**: Set `show-details: always` for granular component status.
