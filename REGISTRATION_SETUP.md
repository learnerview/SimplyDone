# Self-Service API Key Registration - Production Setup Guide

This guide covers setting up and deploying the self-service API key registration feature in production.

## Overview

The self-service registration system enables users to:
1. Visit `/signup` page
2. Enter email and organization name
3. Receive OTP via email
4. Verify OTP to auto-generate API credentials
5. Receive credentials via welcome email

## Production Prerequisites

### 1. SMTP Configuration (Email Service)

#### Recommended: SendGrid

1. Create account at [SendGrid](https://sendgrid.com/)
2. Generate an API key from Settings → API Keys
3. Set environment variables:
   ```bash
   export MAIL_HOST=smtp.sendgrid.net
   export MAIL_PORT=587
   export MAIL_USERNAME=apikey
   export MAIL_PASSWORD=<YOUR_SENDGRID_API_KEY>
   export APP_URL=https://yourdomain.com
   ```

#### Alternative: AWS SES

1. Set up AWS SES in your region
2. Verify sender email address
3. Set environment variables:
   ```bash
   export MAIL_HOST=email-smtp.<region>.amazonaws.com
   export MAIL_PORT=587
   export MAIL_USERNAME=<SMTP_USERNAME>
   export MAIL_PASSWORD=<SMTP_PASSWORD>
   export APP_URL=https://yourdomain.com
   ```

#### Alternative: Custom SMTP Server

Configure your SMTP credentials:
```bash
export MAIL_HOST=your-smtp-server.com
export MAIL_PORT=587
export MAIL_USERNAME=your-username
export MAIL_PASSWORD=your-password
export APP_URL=https://yourdomain.com
```

### 2. Database

PostgreSQL 12+ required:
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/simplydone
export SPRING_DATASOURCE_USERNAME=simplydone_user
export SPRING_DATASOURCE_PASSWORD=<strong_password>
```

### 3. Redis Cache

Redis 6+ for job queue and caching:
```bash
export REDIS_URL=redis://redis-host:6379
```

### 4. Admin Secret

Generate a strong random secret:
```bash
export ADMIN_INITIAL_SECRET=<generate_strong_random_value>
```

**Store securely in:**
- AWS Secrets Manager
- HashiCorp Vault
- Kubernetes Secrets
- Your platform's secret management system

**Never:**
- Commit to version control
- Log to console
- Expose in Docker images

---

## Deployment

### Docker Deployment

```bash
docker build -t simplydone:latest .

docker run \
  -e SPRING_PROFILES_ACTIVE=prod,api,worker \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/simplydone \
  -e SPRING_DATASOURCE_USERNAME=simplydone \
  -e SPRING_DATASOURCE_PASSWORD=<password> \
  -e REDIS_URL=redis://redis:6379 \
  -e MAIL_HOST=smtp.sendgrid.net \
  -e MAIL_PORT=587 \
  -e MAIL_USERNAME=apikey \
  -e MAIL_PASSWORD=<sendgrid_key> \
  -e APP_URL=https://api.yourdomain.com \
  -e ADMIN_INITIAL_SECRET=<secret> \
  -p 8080:8080 \
  simplydone:latest
```

### Docker Compose

```bash
docker-compose up --build -d
```

Update `docker-compose.yml` with production values.

### Kubernetes

```bash
kubectl create secret generic simplydone-secrets \
  --from-literal=ADMIN_INITIAL_SECRET=<secret> \
  --from-literal=SPRING_DATASOURCE_PASSWORD=<password> \
  --from-literal=MAIL_PASSWORD=<password>

kubectl apply -f deployment.yaml
```

### Cloud Platform (Render, Heroku, etc.)

Set environment variables in your platform's dashboard:
```
SPRING_PROFILES_ACTIVE=prod,api,worker
SPRING_DATASOURCE_URL=...
MAIL_HOST=...
MAIL_PASSWORD=...
ADMIN_INITIAL_SECRET=...
```

---

## Testing in Production

### Step 1: Verify Application Health

```bash
curl https://yourdomain.com/actuator/health
# Expected: {"status":"UP"}
```

### Step 2: Request OTP

```bash
curl -X POST https://yourdomain.com/api/auth/signup/request-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "organizationName": "Test Organization"
  }'
```

Expected response:
```json
{
  "success": true,
  "message": "OTP sent to your email. Please check your inbox."
}
```

### Step 3: Check Email

Verify the OTP email was received (may take 1-2 seconds).

### Step 4: Verify OTP

```bash
curl -X POST https://yourdomain.com/api/auth/signup/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "otp": "123456"
  }'
```

Expected response:
```json
{
  "success": true,
  "message": "Registration successful!",
  "data": {
    "apiKey": "sd_sk_...",
    "producerId": "prod_...",
    "organizationName": "Test Organization"
  }
}
```

### Step 5: Login

Visit: `https://yourdomain.com/login`
- Enter the API key received
- Should redirect to dashboard

### Step 6: Use API

```bash
curl -X GET https://yourdomain.com/api/jobs \
  -H "X-API-KEY: sd_sk_..."
```

---

## Database Monitoring

### Check Email Verifications

```sql
SELECT email, verified, verification_attempts, created_at 
FROM email_verifications 
ORDER BY created_at DESC 
LIMIT 20;
```

### Check API Keys

```sql
SELECT id, producer, label, is_admin, active, created_at 
FROM api_keys 
WHERE active = TRUE 
ORDER BY created_at DESC;
```

### Cleanup Expired OTPs

```sql
DELETE FROM email_verifications 
WHERE verified = FALSE 
AND expires_at < NOW();
```

---

## Monitoring & Logging

### Application Health

```bash
# Health check endpoint
curl https://yourdomain.com/actuator/health

# Metrics
curl https://yourdomain.com/actuator/metrics
```

### Log Aggregation

Configure to send logs to:
- CloudWatch (AWS)
- Datadog
- ELK Stack
- Splunk

Key log patterns to monitor:
```
Failed to send OTP email
Failed to send welcome email
Max verification attempts exceeded
Invalid OTP
```

### Email Delivery Monitoring

Monitor in your email service provider:
- SendGrid: https://app.sendgrid.com/
- AWS SES: AWS Console → SES Dashboard
- Check delivery rates, bounce rates, complaints

---

## Security Checklist

- [ ] HTTPS enabled (certificate from Let's Encrypt, ACM, etc.)
- [ ] ADMIN_INITIAL_SECRET stored in secure vault
- [ ] Database credentials in secret management
- [ ] Mail credentials in secret management (SendGrid API key)
- [ ] Firewall configured (restrict admin endpoints if needed)
- [ ] Rate limiting enabled (check SecurityConfig)
- [ ] Database backups configured
- [ ] Monitoring and alerting enabled
- [ ] CORS configured properly
- [ ] Secrets not in Docker image layers
- [ ] Application logs don't contain sensitive data
- [ ] OTP expiry set to reasonable time (default: 10 minutes)
- [ ] Max verification attempts enforced (default: 3)

---

## Troubleshooting Production Issues

### Issue: "Failed to send OTP email"

**Check:**
1. SMTP credentials are correct
2. Network connectivity to SMTP server
3. SMTP service logs for rejections
4. Email quota not exceeded

**Solution:**
```bash
# Test SMTP connectivity
telnet smtp.sendgrid.net 587

# Check application logs
tail -f /var/log/simplydone/app.log | grep -i email
```

### Issue: OTP emails delayed

**Check:**
1. Email provider queue (SendGrid dashboard)
2. Network latency
3. Database transaction logs

**Solution:**
- Check SendGrid/SES statistics
- Increase SMTP timeout in application.properties
- Verify database performance

### Issue: Users can't login after registration

**Check:**
```sql
SELECT * FROM api_keys 
WHERE producer LIKE 'prod_%' 
AND active = FALSE;
```

**Solution:**
```sql
UPDATE api_keys SET active = TRUE 
WHERE producer = 'prod_...' 
AND active = FALSE;
```

### Issue: High OTP verification failures

**Check:**
```sql
SELECT email, verification_attempts, otp_code 
FROM email_verifications 
WHERE verified = FALSE 
ORDER BY created_at DESC 
LIMIT 10;
```

**Solution:**
- Check email delivery (may be going to spam)
- Verify OTP format is correct
- Consider increasing max attempts

---

## Performance Tuning

### Database

```sql
-- Ensure indexes are created
CREATE INDEX idx_email_verified ON email_verifications(email, verified);
CREATE INDEX idx_expires_at ON email_verifications(expires_at);
CREATE INDEX idx_api_keys_producer ON api_keys(producer);
```

### Cache

Redis is used for job queue. Monitor:
```bash
redis-cli INFO memory
redis-cli DBSIZE
```

### JVM Heap

Production setting in .env:
```bash
JAVA_TOOL_OPTIONS=-Xmx1g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

Adjust based on workload and available memory.

---

## Configuration Reference

### Environment Variables

```bash
# Application
SPRING_PROFILES_ACTIVE=prod,api,worker
PORT=8080
APP_URL=https://yourdomain.com

# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/simplydone
SPRING_DATASOURCE_USERNAME=simplydone
SPRING_DATASOURCE_PASSWORD=<password>

# Cache
REDIS_URL=redis://host:6379

# Email
MAIL_HOST=smtp.sendgrid.net
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=<api_key>

# Admin
ADMIN_INITIAL_SECRET=<secret>

# JVM
JAVA_TOOL_OPTIONS=-Xmx1g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### Application Properties

```properties
# OTP Settings
simplydone.registration.otp-validity-minutes=10
simplydone.registration.max-verification-attempts=3

# Email
simplydone.registration.sender-email=${MAIL_USERNAME}
simplydone.registration.sender-name=SimplyDone

# Queue
simplydone.queue.max-depth=10000

# Rate Limiting
simplydone.rate-limit.requests-per-minute=60
simplydone.rate-limit.window-seconds=60

# Retry
simplydone.retry.max-attempts=3
simplydone.retry.initial-delay-seconds=5
```

---

## Support & Incidents

### Reporting Issues

1. Check application logs for error messages
2. Monitor metrics in SendGrid/SES dashboard
3. Query database to verify data integrity
4. Check network connectivity to external services

### Rollback Plan

If production issues occur:
1. Disable signup page (`/signup` route)
2. Revert to previous deployment
3. Investigate root cause
4. Redeploy with fix

---

## Maintenance

### Regular Tasks

- **Daily**: Monitor email delivery rates, error logs
- **Weekly**: Check database disk usage, backup status
- **Monthly**: Review user registration metrics, purge old OTPs
- **Quarterly**: Update SMTP credentials, security audit

### Cleanup Old Data

```sql
-- Delete expired verifications older than 7 days
DELETE FROM email_verifications 
WHERE verified = FALSE 
AND created_at < NOW() - INTERVAL '7 days';

-- Archive old registrations (optional)
-- Export to data warehouse before deletion
```

---

## API Reference (Production)

### Public Endpoints

```
POST /api/auth/signup/request-otp
POST /api/auth/signup/verify-otp
GET  /signup
GET  /login
```

### Authenticated Endpoints

All require `X-API-KEY` header.

See documentation for full API reference.
