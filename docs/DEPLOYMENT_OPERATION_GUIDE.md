# Deployment & Operations Guide

SimplyDone is designed for containerized deployment with high availability and scalability in mind.

## Deployment Platforms

### Render (Recommended)
SimplyDone includes a `render.yaml` Blueprint for automated infrastructure provisioning.
1. Connect repository to Render.
2. Select the Blueprint for deployment.
3. Configure the `simplydone-secrets` group with your SMTP credentials.

### Docker & Docker Compose
For self-hosted environments or local replication of production stacks.
```bash
docker compose -f docker-compose.yml up -d
```
The stack includes the application, a PostgreSQL instance, and a Redis cache.

### Kubernetes
For enterprise-scale deployments, utilize the structured deployment manifests to manage replicas, resource limits, and liveness probes.

## Operational Checklist

### 1. Security & Secrets
- Ensure `DATABASE_URL` and `REDIS_URL` are injected via secure environment variables.
- Update the default PostgreSQL password from the development default.
- Enable TLS/SSL for all public-facing endpoints.

### 2. Monitoring
- Integrates with Prometheus and Grafana via the `/actuator/prometheus` endpoint.
- Monitor `jvm.memory.used` and `http.server.requests` for performance trends.

### 3. Queue Maintenance
- Periodically audit the Dead Letter Queue (DLQ) via the Admin interface.
- Implement cleanup jobs (using the `CLEANUP` job type) to purge old job logs and temporary assets.

## Scaling Strategy
- **Horizontal Scaling**: Increase the number of application instances. All instances share the same Redis queues and PostgreSQL database, allowing for distributed job processing.
- **Vertical Scaling**: Optimize the `maximum-pool-size` and worker polling intervals as resources are added.

## Disaster Recovery
- **Database Backups**: Schedule daily PostgreSQL dumps.
- **Redis Persistence**: Use RDB or AOF snapshots to protect against transient data loss in the queuing layer.
