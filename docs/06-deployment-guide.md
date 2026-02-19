# Deployment Guide

This guide covers deploying SimplyDone to production environments including Render, AWS, Docker, and self-hosted infrastructure.

## Deployment overview

SimplyDone is containerized and cloud-native. It requires:
- PostgreSQL 15+ database
- Redis 7+ cache store
- Java 17+ runtime

### Recommended deployment targets

| Platform | Best for | Cost | Setup time |
|---|---|---|---|
| **Render** | Small to medium | Free tier available | <5 minutes |
| **AWS (EC2 + RDS)** | Scalable workloads | Variable | 15-30 minutes |
| **Docker + Orchest** | Self-hosted, full control | Hosting costs | 20-40 minutes |
| **DigitalOcean App Platform** | Simple deployments | $5-12/month | 10-15 minutes |
| **Railway** | Quick deployments | $5-50/month | <5 minutes |

## Render deployment (recommended for quick start)

Render provides a managed PostgreSQL and Redis services on the free tier. The `render.yaml` file in the repository defines the entire infrastructure.

### Prerequisites

1. **GitHub account** with repository access
2. **Render account** (free at [render.com](https://render.com))
3. **Push code to GitHub** repository

### Step 1: Connect repository to Render

1. Log in to [Render](https://render.com)
2. Click "New +" → "Blueprint"
3. Connect your GitHub account
4. Select the repository containing SimplyDone
5. Select the branch to deploy (e.g., `main`)
6. Click "Create"

### Step 2: Configure environment variables

Render reads `render.yaml` for infrastructure configuration. The file is pre-configured to:
- Create PostgreSQL service (free tier, 1GB)
- Create Redis service (free tier, 1GB)
- Build Docker image automatically
- Deploy web service with health checks

No additional configuration needed unless customizing ports or resources.

### Step 3: Monitor deployment

1. Render automatically builds the Docker image
2. Provisions PostgreSQL and Redis services
3. Deploys the web service
4. Runs health checks

Deployment takes 5-10 minutes. Check the deployment logs for progress.

### Step 4: Verify deployment

Once deployment completes:

```bash
curl https://your-app-name.onrender.com/actuator/health
```

Should return:
```json
{"status": "UP", ...}
```

### Environment variables (Render auto-sets these)

Render automatically injects:
- `DATABASE_URL` — PostgreSQL connection string
- `REDIS_URL` — Redis connection string

You can add additional variables in Render dashboard:
- `EMAIL_ENABLED=true`
- `EMAIL_API_KEY=your_api_key`
- `SPRING_PROFILES_ACTIVE=prod`

### Auto-scaling

Render auto-scales based on:
- CPU usage
- Memory usage
- HTTP request volume

Minimum 1 instance, maximum configurable in dashboard.

### Troubleshooting Render deployment

**Deployment fails during build**
- Check build logs in Render dashboard
- Verify Dockerfile is correct: `cat Dockerfile`
- Ensure Maven dependencies are available

**Application crashes after deployment**
- Check runtime logs in Render dashboard
- Verify database connection: `DATABASE_URL` environment variable set
- Check Redis connection: `REDIS_URL` environment variable set
- Look for "Connection refused" errors

**Health check failing**
- Application takes 30-60 seconds to start
- Render waits 60 seconds before first health check
- Check `/actuator/health` endpoint in logs
- Verify database migrations completed

## AWS Deployment (EC2 + RDS + ElastiCache)

For production workloads requiring scalability and high availability.

### Prerequisites

1. **AWS account** with billing enabled
2. **AWS CLI** installed and configured
3. **Docker image pushed to ECR** (Elastic Container Registry)

### Step 1: Create RDS PostgreSQL instance

```bash
aws rds create-db-instance \
  --db-instance-identifier simplydone-db \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --master-username admin \
  --master-user-password <strong-password> \
  --allocated-storage 20 \
  --db-name simplydone
```

Wait for instance to be available (5-10 minutes).

### Step 2: Create ElastiCache Redis cluster

```bash
aws elasticache create-cache-cluster \
  --cache-cluster-id simplydone-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --num-cache-nodes 1
```

### Step 3: Push Docker image to ECR

```bash
# Create ECR repository
aws ecr create-repository --repository-name simplydone

# Build and push image
docker build -t simplydone .
docker tag simplydone:latest <account-id>.dkr.ecr.<region>.amazonaws.com/simplydone:latest
docker push <account-id>.dkr.ecr.<region>.amazonaws.com/simplydone:latest
```

### Step 4: Launch EC2 instance

1. **Choose AMI:** Amazon Linux 2 or Ubuntu 22.04
2. **Instance type:** t3.small or larger
3. **Configure security group:**
   - Inbound: Port 8080 from 0.0.0.0/0
   - Outbound: All traffic
4. **Add user data script:**

```bash
#!/bin/bash
yum update -y
amazon-linux-extras install docker -y
systemctl start docker

aws ecr get-login-password | docker login --username AWS --password-stdin <account-id>.dkr.ecr.<region>.amazonaws.com

docker run -d \
  --name simplydone \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL="jdbc:postgresql://<rds-endpoint>:5432/simplydone" \
  -e DATABASE_USER=admin \
  -e DATABASE_PASSWORD=<password> \
  -e REDIS_URL="redis://<redis-endpoint>:6379" \
  -e EMAIL_ENABLED=false \
  <account-id>.dkr.ecr.<region>.amazonaws.com/simplydone:latest
```

### Step 5: Create load balancer (optional for HA)

```bash
aws elbv2 create-load-balancer \
  --name simplydone-lb \
  --subnets <subnet-ids> \
  --security-groups <sg-id>
```

Register EC2 instances with the load balancer for traffic distribution.

### Step 6: Setup CloudWatch monitoring

AWS automatically monitors:
- EC2 CPU/Memory
- RDS database performance
- ElastiCache Redis performance

Set up alarms for:
- High EC2 CPU (> 80%)
- RDS connections limit (> 90%)
- Redis eviction rate

## Docker Compose for self-hosted

For deploying on your own infrastructure (VPS, Kubernetes, Docker Swarm).

### Single-machine deployment

Create `docker-compose-prod.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: <strong-password>
      POSTGRES_DB: simplydone
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    image: simplydone:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: "jdbc:postgresql://postgres:5432/simplydone"
      DATABASE_USER: postgres
      DATABASE_PASSWORD: ${DB_PASSWORD}
      REDIS_URL: "redis://redis:6379"
      EMAIL_ENABLED: "true"
      EMAIL_API_KEY: ${EMAIL_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  postgres-data:
  redis-data:
```

Deploy:

```bash
# Build image
docker build -t simplydone .

# Start services
docker compose -f docker-compose-prod.yml up -d

# Monitor
docker compose -f docker-compose-prod.yml logs -f app
```

### Multi-machine deployment with Swarm

Initialize Swarm:

```bash
docker swarm init
```

Deploy stack:

```bash
docker stack deploy -c docker-compose-prod.yml simplydone
```

## Kubernetes deployment

For large-scale, self-healing deployments.

Create `k8s-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: simplydone
spec:
  replicas: 3
  selector:
    matchLabels:
      app: simplydone
  template:
    metadata:
      labels:
        app: simplydone
    spec:
      containers:
      - name: simplydone
        image: your-registry/simplydone:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: simplydone-secrets
              key: database-url
        - name: REDIS_URL
          valueFrom:
            secretKeyRef:
              name: simplydone-secrets
              key: redis-url
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
---
apiVersion: v1
kind: Service
metadata:
  name: simplydone-service
spec:
  selector:
    app: simplydone
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

Deploy:

```bash
# Create secrets
kubectl create secret generic simplydone-secrets \
  --from-literal=database-url='...' \
  --from-literal=redis-url='...'

# Deploy
kubectl apply -f k8s-deployment.yaml

# Monitor
kubectl logs -f deployment/simplydone
```

## Production checklist

Before going live, verify:

### Code & Configuration
- [ ] `render.yaml` or `docker-compose.yml` configured correctly
- [ ] Environment variables set for production
- [ ] `application-prod.properties` reviewed
- [ ] Email API key valid and tested
- [ ] Database password is strong (20+ characters)
- [ ] Redis password set (if publicly accessible)

### Infrastructure
- [ ] Database backup strategy defined
- [ ] Database connection pooling tuned
- [ ] Redis persistence enabled
- [ ] Firewall rules restrict access to ports 5432, 6379
- [ ] HTTPS/TLS enabled for web traffic
- [ ] Health checks configured
- [ ] Monitoring and alerting enabled

### Deployment
- [ ] Application deployed and verified
- [ ] Health endpoint responding
- [ ] Database migrations completed
- [ ] Job queue working (test with sample job)
- [ ] Logs captured and stored
- [ ] Rollback plan documented

### Security
- [ ] Secrets not in code repository
- [ ] Database credentials in environment variables
- [ ] API keys in environment variables
- [ ] Inbound traffic filtered
- [ ] SSL certificates valid
- [ ] Authentication enabled on admin endpoints

### Performance
- [ ] Database connection pool size appropriate
- [ ] Redis memory limit configured
- [ ] Worker thread interval tuned
- [ ] Job timeout values reasonable
- [ ] Rate limiting configured

## Zero-downtime deployments

For zero-downtime updates:

### Render
- Render automatically creates new instances
- Blue-green deployment without service interruption
- Previous version remains available during transition
- Automatic rollback if health checks fail

### Docker Swarm
```bash
docker service update \
  --image your-registry/simplydone:v2 \
  simplydone_app
```

Swarm updates instances one by one, maintaining availability.

### Kubernetes
```bash
kubectl set image deployment/simplydone \
  simplydone=your-registry/simplydone:v2
```

Kubernetes rolling update maintains minimum replicas during deployment.

## Monitoring and observability

### Application logs

All logs written to stdout/stderr are captured by the deployment platform:

```bash
# Render
# View in Render dashboard Logs tab

# Docker Compose
docker compose logs -f app

# Kubernetes
kubectl logs -f deployment/simplydone
```

### Metrics

Access Prometheus metrics at `/actuator/metrics`:

```bash
curl https://your-app/actuator/metrics | jq
```

Key metrics to monitor:
- `jvm.memory.used` — JVM heap memory
- `process.cpu.usage` — CPU utilization
- `http.server.requests` — Request latency
- `process.uptime` — Application uptime

### Health checks

All platforms use `/actuator/health` for readiness:

```bash
curl https://your-app/actuator/health
```

Response indicates:
- `UP` — Ready to serve traffic
- `DOWN` — Not ready, remove from load balancer

### Database monitoring

PostgreSQL connection status:

```sql
SELECT count(*) FROM pg_stat_activity;
```

Monitor:
- Active connections
- Database size growth
- Slow query logs
- Autovacuum performance

### Redis monitoring

```bash
redis-cli INFO
```

Monitor:
- Used memory
- Connected clients
- Eviction rate
- Hit/miss ratio

## Scaling

### Horizontal scaling (more instances)

**Render:** Increase instance count in dashboard (auto-scales by default)

**Docker Swarm:** Increase replicas
```bash
docker service scale simplydone_app=5
```

**Kubernetes:** Increase pod replicas
```bash
kubectl scale deployment simplydone --replicas 5
```

### Vertical scaling (larger instances)

**Render:** Upgrade instance plan in dashboard

**Docker:** Allocate more memory/CPU to container

**Kubernetes:** Increase resource requests/limits in deployment

## Disaster recovery

### Database backups

**Render:** Automatic backups, configurable retention

**AWS RDS:** Enable automated backups
```bash
aws rds modify-db-instance \
  --db-instance-identifier simplydone-db \
  --backup-retention-period 30
```

**Self-hosted:** Create backup schedule
```bash
# Daily PostgreSQL backup
0 2 * * * docker exec postgres pg_dump -U postgres simplydone > /backups/db.sql
```

### Testing recovery

Monthly: Restore from backup to verify integrity
```bash
psql -U postgres simplydone < /backups/db.sql
```

## Support resources

- Render docs: [render.com/docs](https://render.com/docs)
- AWS RDS: [aws.amazon.com/rds](https://aws.amazon.com/rds)
- Docker docs: [docker.com/get-started](https://docker.com/get-started)
- Kubernetes: [kubernetes.io/docs](https://kubernetes.io/docs)
