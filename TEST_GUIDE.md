# SimplyDone Application - Complete Testing Guide

## Setup Information

Your credentials are configured in `.env`:
- **Email**: sumit033345@gmail.com
- **Gmail App Password**: fguz qxz pfhx qffq
- **Admin Secret**: sumit_shukla

---

## Step 1: Start the Application with Docker Compose

```bash
cd /d/LocalProjects/SimplyDone

# Build and start all services (Postgres, Redis, App)
docker-compose up --build -d

# Check if all services are running
docker-compose ps
```

**Expected Output:**
```
NAME              STATUS
postgres          Up (healthy)
redis             Up (healthy)
simplydone-app    Up
```

**Wait 15-20 seconds** for the application to fully start and initialize.

---

## Step 2: Verify Application is Running

```bash
# Check health endpoint
curl http://localhost:8080/actuator/health
```

**Expected Response:**
```json
{"status":"UP"}
```

If you get connection refused, wait 10 more seconds.

---

## Step 3: Get Your Admin API Key

When the application starts, it automatically creates an admin key with:
- **Producer**: admin
- **API Key**: (auto-generated, stored in database)
- **Secret**: sumit_shukla (value from ADMIN_INITIAL_SECRET)

**Query the database to get the admin API key:**

```bash
# Access PostgreSQL
docker-compose exec postgres psql -U postgres -d simplydone

# Inside psql, run:
SELECT api_key, producer, is_admin FROM api_keys WHERE producer = 'admin';
```

**Output should look like:**
```
         api_key          | producer | is_admin
--------------------------+----------+---------
sd_sk_abc123def456...     | admin    | t
```

**Save this API key** - you'll need it for admin operations.

---

## Step 4: Test Admin Access (Optional)

```bash
# Use your admin API key
curl -X GET http://localhost:8080/api/admin/stats \
  -H "X-API-KEY: sd_sk_abc123def456..."
```

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "queued": 0,
    "processing": 0,
    "completed": 0,
    "failed": 0
  }
}
```

---

## Step 5: Test User Signup (Self-Service Registration)

### 5a. Open the Signup Page

Open in your browser:
```
http://localhost:8080/signup
```

You should see a beautiful signup form with:
- Email input field
- Organization name input field
- "Get OTP Code" button

### 5b. Enter Your Details

**In the signup form, enter:**
- **Email**: Your test email (or sumit033345@gmail.com if testing email delivery)
- **Organization Name**: Test Company

**Example:**
```
Email: test.user@example.com
Organization Name: Test Company Inc
```

**Click:** "Get OTP Code"

### 5c. Check for OTP Email

Check the email inbox for:
- **From**: SimplyDone
- **Subject**: SimplyDone - Email Verification Code
- **Contains**: 6-digit OTP code

**If using your Gmail (sumit033345@gmail.com):**
- OTP will be sent to sumit033345@gmail.com
- Check inbox within 5 seconds
- If not in inbox, check Promotions or Spam folders

**Copy the 6-digit code** (e.g., 123456)

### 5d. Enter OTP

In the OTP modal that appears on the signup page:
- Enter the 6 digits one by one
- Each field accepts one digit
- Auto-focuses to next field

**Click:** "Verify & Create Key"

### 5e: View Your API Credentials

On success, you'll see:

```
🎉 Registration Successful!

Organization: Test Company Inc
Producer ID: prod_abc123def456
API Key: sd_sk_xyz789...
```

**Save these credentials!**

---

## Step 6: Login with Your New API Key

### 6a. Open Login Page

```
http://localhost:8080/login
```

### 6b. Enter Your API Key

- Copy the API Key from signup success screen
- Paste it in the input field (starts with `sd_sk_`)

**Click:** "Sign In"

### 6c. Access Dashboard

You should be redirected to:
```
http://localhost:8080/
```

And see the SimplyDone dashboard with:
- Jobs list (empty)
- Stats overview
- Sidebar navigation

---

## Step 7: Test API Endpoints

With your API key, test the job submission API:

```bash
# Store your API key in a variable
API_KEY="sd_sk_your_key_here"

# Test 1: Get your jobs (should be empty)
curl -X GET http://localhost:8080/api/jobs \
  -H "X-API-KEY: $API_KEY"

# Response:
# {
#   "success": true,
#   "data": {
#     "content": [],
#     "pageable": {...},
#     "totalElements": 0
#   }
# }

# Test 2: Submit a job
curl -X POST http://localhost:8080/api/jobs \
  -H "X-API-KEY: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "webhook",
    "payload": {
      "url": "https://example.com/webhook",
      "method": "POST",
      "headers": {"Authorization": "Bearer token"}
    },
    "nextRunAt": "2026-05-10T12:00:00Z"
  }'

# Response:
# {
#   "success": true,
#   "message": "Job queued",
#   "data": {
#     "id": "job-uuid",
#     "status": "QUEUED",
#     "createdAt": "2026-05-09T...",
#     "nextRunAt": "2026-05-10T..."
#   }
# }

# Test 3: Get jobs again (should show your job)
curl -X GET http://localhost:8080/api/jobs \
  -H "X-API-KEY: $API_KEY"
```

---

## Step 8: Test with Multiple Users

### 8a. Create Second User

1. Open incognito/private window (to avoid login cache)
2. Visit `http://localhost:8080/signup`
3. Enter different email: `test2@example.com`
4. Organization: `Another Company`
5. Get OTP from email
6. Verify OTP
7. Login with new API key

### 8b. Verify Isolation

Each API key only sees its own jobs:

```bash
# User 1's API key
curl -X GET http://localhost:8080/api/jobs \
  -H "X-API-KEY: sd_sk_user1_key"

# User 2's API key
curl -X GET http://localhost:8080/api/jobs \
  -H "X-API-KEY: sd_sk_user2_key"

# Both should see different job lists
```

---

## Troubleshooting

### Issue: "OTP not received"

**Check:**
1. Gmail inbox and spam folder
2. If using test email, emails won't be sent

**Solution:**
```bash
# Check application logs
docker-compose logs simplydone-app | grep -i email

# If SMTP error, verify Gmail app password is correct
# Format: fguz qxz pfhx qffq (with spaces)
```

### Issue: "Connection refused on port 8080"

**Solution:**
```bash
# Wait for app to start
sleep 20

# Check logs
docker-compose logs simplydone-app

# If still failing, restart
docker-compose restart simplydone-app
```

### Issue: "Database connection error"

**Solution:**
```bash
# Check Postgres is healthy
docker-compose logs postgres

# Restart Postgres
docker-compose down
docker-compose up --build -d
```

### Issue: "Invalid OTP"

**Check:**
1. You entered correct 6 digits from email
2. OTP hasn't expired (valid for 10 minutes)
3. You haven't exceeded 3 attempts

**Solution:** Request new OTP and try again.

---

## Admin Operations

### Create Additional Admin Keys (via API)

```bash
ADMIN_KEY="sd_sk_admin_key_from_db"

# Create a new admin API key
curl -X POST http://localhost:8080/api/admin/keys \
  -H "X-API-KEY: $ADMIN_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "label": "Developer Admin Key",
    "producer": "admin_developer",
    "admin": true
  }'
```

### View All API Keys

```bash
ADMIN_KEY="sd_sk_admin_key"

curl -X GET http://localhost:8080/api/admin/keys \
  -H "X-API-KEY: $ADMIN_KEY"
```

### View Queue Statistics

```bash
ADMIN_KEY="sd_sk_admin_key"

curl -X GET http://localhost:8080/api/admin/stats \
  -H "X-API-KEY: $ADMIN_KEY"
```

---

## What Each Component Does

| Component | Purpose | Details |
|-----------|---------|---------|
| **Signup (/signup)** | User registration | Email + OTP verification, auto-creates API key |
| **Login (/login)** | User authentication | API key based login |
| **Dashboard (/)** | UI Management | Jobs list, stats, admin console |
| **/api/auth/signup/request-otp** | Request OTP | Sends OTP email to user |
| **/api/auth/signup/verify-otp** | Create API key | Verifies OTP, creates credentials |
| **/api/jobs** | Job Management | POST to create, GET to list |
| **/api/admin/keys** | Admin Key Management | Create, list, revoke API keys |
| **/api/admin/stats** | Statistics | Queue depth, job counts |

---

## Testing Checklist

- [ ] Application starts without errors
- [ ] Admin key created automatically
- [ ] Signup page loads
- [ ] OTP email received
- [ ] OTP verified successfully
- [ ] Login works with new API key
- [ ] Dashboard loads
- [ ] API endpoints return correct responses
- [ ] Can create jobs via API
- [ ] Can list jobs via API
- [ ] Second user signup works
- [ ] Users can't see each other's jobs

---

## Docker Commands Reference

```bash
# Start all services
docker-compose up --build -d

# Stop all services
docker-compose down

# View logs
docker-compose logs -f simplydone-app

# Access Postgres
docker-compose exec postgres psql -U postgres -d simplydone

# Restart a service
docker-compose restart simplydone-app

# Remove volumes (WARNING: deletes data)
docker-compose down -v

# Check service status
docker-compose ps

# View resource usage
docker stats
```

---

## Next Steps

1. ✅ Run through the complete testing checklist
2. Monitor logs during testing
3. Test with real email (sumit033345@gmail.com)
4. Create multiple test users
5. Test API with job submissions
6. Check database directly for verification

All set! Start with Step 1. 🚀
