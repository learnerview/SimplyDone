# SimplyDone: Enterprise Multi-Tenant Job Scheduling Platform 🚀

Welcome to **SimplyDone**! SimplyDone is a high-availability, distributed job-scheduling and execution engine. It provides a robust "Job-as-a-Service" (JaaS) platform that allows multiple independent teams or external users (tenants) to schedule background tasks with guaranteed isolation, observability, and fault tolerance.

---

## 👩‍💼 For Non-Technical Users: What is SimplyDone?

Imagine you run an online store and you need to send 10,000 emails, generate 500 PDF reports, and process 1,000 payments. Doing all of this at once would crash your website. 

**SimplyDone is the solution.** It acts like an intelligent "to-do list manager" for your software systems. 
1. **You tell SimplyDone what needs to be done** (e.g., "Send an email to John").
2. **SimplyDone puts it in a queue**, categorized by priority (High, Normal, Low).
3. **SimplyDone's workers execute the tasks** in the background, making sure your main website stays fast.
4. If a task fails (e.g., the email server is down), **SimplyDone automatically retries** it later. If it fails too many times, it goes to the **Dead Letter Queue (DLQ)**, where an administrator can manually review and retry it.

### How to use the SimplyDone Dashboard
1. **Login**: Go to `http://localhost:8080/login`. Enter your API key (e.g., `sd_sk_test_user1`). Your API key is your secure password.
2. **Dashboard**: View a high-level summary of your tasks (queued, running, succeeded, or failed).
3. **Jobs**: Submit new tasks by specifying a target URL and the data you want to send. You can also view a real-time list of all your past tasks.
4. *(Admins Only)* **Admin Console**: Manage API keys for your team, issue new keys, revoke old ones, and clear stuck queues.
5. *(Admins Only)* **Dead Letter Queue**: View tasks that failed permanently and retry them with the click of a button.

---

## 🧑‍💻 For Developers: Quick Start Guide

### 1. Requirements & Launch
SimplyDone requires PostgreSQL (Database) and Redis (Queue Engine). You can spin up the entire stack using Docker:

```bash
docker-compose up -d
mvn spring-boot:run
```

By default, the application runs on port `8080`.

### 2. Built-in Test Keys
The database is automatically seeded with the following keys for testing:
- **Global Admin**: `sd_sk_test_admin`
- **Tenant 1**: `sd_sk_test_user1`
- **Tenant 2**: `sd_sk_test_user2`

### 3. Using the Java SDK
SimplyDone includes a minimal, built-in Java SDK (`SimplyDoneClient.java`) that allows you to easily submit jobs from any external Java application.

```java
import com.learnerview.simplydone.sdk.SimplyDoneClient;
import java.util.Map;

public class App {
    public static void main(String[] args) throws Exception {
        // Initialize the client with your API Key
        SimplyDoneClient client = new SimplyDoneClient("http://localhost:8080", "sd_sk_test_user1");

        // Submit a job
        String result = client.submitJob(
            "https://webhook.site/your-webhook-url", 
            Map.of("message", "Hello SimplyDone!", "userId", 123)
        );
        
        System.out.println("Job submitted successfully: " + result);
    }
}
```

---

## 🏗️ Architecture & Core Features

### 1. True Multi-Tenancy & Security
- **Strict Data Isolation**: Every job is tied to a `producer` (Tenant ID). Regular tenants can never see or modify jobs belonging to other tenants.
- **Stateless API Keys**: All authentication is handled via the `X-API-KEY` header.
- **Role-Based Access Control (RBAC)**: Only keys with the `admin` flag set to `true` can access the Admin and DLQ features.

### 2. High-Performance Queuing
- **Redis-Backed**: Uses Redis `ZSET` (Sorted Sets) for atomic, sub-millisecond priority queuing (High, Normal, Low).
- **Worker/API Profiles**: You can scale SimplyDone by running instances in `worker` mode (only executes jobs) and `api` mode (only handles UI and REST requests).

### 3. The "Un-Card" Sectional UI
SimplyDone features a fully custom, enterprise-grade dark mode dashboard built with Vanilla HTML/CSS/JS. It utilizes a **Sectional Grid** layout (no floating cards), providing zero-overflow responsiveness and real-time Server-Sent Events (SSE) updates.

---

## 📚 API Specification

### Submit a Job
- **POST** `/api/jobs`
- **Headers**: `X-API-KEY: your_api_key`
```json
{
  "jobType": "external",
  "priority": "HIGH",
  "executionEndpoint": "https://api.example.com/webhook",
  "payload": { "foo": "bar" }
}
```

### Admin REST Boundaries
All `/api/admin/**` routes are strictly protected by Spring Security's `@PreAuthorize("hasRole('ADMIN')")`.
- `GET /api/admin/stats` - Global cluster metrics.
- `GET /api/admin/dlq` - List of all permanently failed jobs.
- `GET /api/admin/keys` - List of all issued merchant API tokens.
- `POST /api/admin/keys` - Issue a new rotating key for a tenant.

---

## 🛡️ Resilience & Fault Tolerance
1. **In-Flight Protection**: If a worker crashes while executing a job, a background Reaper service automatically re-queues the "orphaned" job after the lease timeout.
2. **Exponential Backoff**: Failing jobs are retried with increasing delays (e.g., 5s, 10s, 20s) to prevent overwhelming downstream services.
3. **Circuit Breakers**: External HTTP executions are wrapped in Resilience4j circuit breakers to prevent cascading failures.
4. **Key Rotation UPSERT**: Creating a key for an existing tenant automatically revokes their old key and issues a new one without causing database constraint errors.
