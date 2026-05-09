# Quick Reference: What to Enter at Each Step

## Your Credentials

```
Gmail Address:    sumit033345@gmail.com
Gmail Password:   fguz vqxz pfhx qffq
Admin Secret:     sumit_shukla
```

---

## Step-by-Step: What You'll See & Enter

### 1️⃣ Start Docker (Terminal)

```bash
cd /d/LocalProjects/SimplyDone
docker-compose up --build -d
```

**Wait 15-20 seconds** for startup.

---

### 2️⃣ Open Signup Page (Browser)

**URL:** `http://localhost:8080/signup`

**Page shows:**
```
📝 SimplyDone
Get Started
Create your API access in minutes
```

---

### 3️⃣ Fill Signup Form

**You see these fields:**

| Field | What to Enter | Example |
|-------|---------------|---------|
| Email Address | Your test email | `test@example.com` |
| Organization Name | Your company name | `My Company` |

**IMPORTANT:** If testing email delivery, use:
- Email: `sumit033345@gmail.com` (so you receive the OTP)
- Organization: Any name you want

**Then click:** `Get OTP Code`

---

### 4️⃣ Wait for Email

**Check your email inbox** (sumit033345@gmail.com)

**You'll receive:**
```
From: SimplyDone
Subject: SimplyDone - Email Verification Code

Your verification code is:

  [123456]

This code expires in 10 minutes.
```

**Copy the 6 digits** (example: 123456)

---

### 5️⃣ Enter OTP in Modal

**Modal appears on signup page:**

```
Verify Your Email

We've sent a 6-digit code to: test@example.com

Enter Verification Code
[_][_][_][_][_][_]
          ↑
      Paste OTP here
```

**Paste the code** → Auto-fills each box → Auto-focuses to next

**Then click:** `Verify & Create Key`

---

### 6️⃣ Success! 🎉

**You see:**
```
🎉 Registration Successful!
Your API key has been sent to your email.

Organization:     My Company
Producer ID:      prod_abc123def456xyz
API Key:          sd_sk_xyz789abc123...
```

**⚠️ SAVE THIS!** → Copy and keep the API Key

---

### 7️⃣ Login (New Tab)

**URL:** `http://localhost:8080/login`

**You see:**
```
SimplyDone
Please enter your API Key to continue

[                    ]
        ↑ Paste API Key here

Sign In
```

**Paste the API Key** → Click `Sign In`

---

### 8️⃣ Dashboard

**Redirects to:** `http://localhost:8080/`

**You see:**
```
Dashboard
Infrastructure | API Keys | Registry

Total Queued: 0
Processing: 0
Completed: 0
Failed: 0
```

✅ **Success!** Application is working!

---

## Admin Key (For Admin Operations)

**After Docker starts:**

```bash
# Get admin API key from database
docker-compose exec postgres psql -U postgres -d simplydone

# In psql shell, type:
SELECT api_key FROM api_keys WHERE producer = 'admin';
```

**You'll see something like:**
```
         api_key
--------------------------
sd_sk_abc123def456xyz
```

**This is your ADMIN API KEY** - Use for `/api/admin/*` endpoints

---

## What Happens in Background

| Step | What Happens |
|------|--------------|
| Signup form submit | Email verification request sent |
| Email received | OTP stored in database (expires 10 min) |
| OTP entered | OTP validated, API key generated |
| Login | API key checked in database |
| Dashboard loads | API calls use X-API-KEY header |

---

## Important Notes

✅ **Remember:**
- API Key = Your identity
- Each user has unique Producer ID
- Admin key is different from user keys
- OTP valid only 10 minutes
- Max 3 wrong OTP attempts

⚠️ **Keep Secure:**
- Never share your API key
- Never commit to Git
- Don't log it to console
- Use HTTPS in production

---

## Testing Commands (Optional)

### After Login, Test API:

```bash
# Get your API key from dashboard or email

API_KEY="sd_sk_your_key_here"

# List your jobs (empty initially)
curl http://localhost:8080/api/jobs \
  -H "X-API-KEY: $API_KEY"

# Create a job
curl -X POST http://localhost:8080/api/jobs \
  -H "X-API-KEY: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "jobType": "webhook",
    "payload": {"url": "https://example.com/hook"},
    "nextRunAt": "2026-05-15T10:00:00Z"
  }'
```

---

## Quick Troubleshooting

| Problem | Solution |
|---------|----------|
| Port 8080 refused | Wait 20 seconds, app is starting |
| Email not received | Check spam folder, verify email correct |
| OTP invalid | Get new OTP, max 3 attempts per OTP |
| Can't login | Check API key is exact copy (includes `sd_sk_`) |
| Database error | Run `docker-compose down && docker-compose up --build -d` |

---

## Docker Status Checks

```bash
# Check all services running
docker-compose ps

# Check app logs for errors
docker-compose logs simplydone-app

# Check database is accessible
docker-compose exec postgres psql -U postgres -d simplydone -c "SELECT 1"

# View all API keys created
docker-compose exec postgres psql -U postgres -d simplydone -c "SELECT producer, label, is_admin FROM api_keys"
```

---

**Ready to test? Start with:** `docker-compose up --build -d` 🚀
