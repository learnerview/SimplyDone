# Technical Architecture

SimplyDone architecture is built on the principles of scalability, fault tolerance, and clear separation of concerns.

## System Model

### 1. Ingestion Layer
- **REST API**: Standardized JSON endpoints for job submission.
- **Thymeleaf UI**: Modular web views for human-driven job management.
- **Rate Limit Service**: Redis-backed sliding window counter protecting system resources.

### 2. Orchestration Layer
- **Job Service**: Handles validation, ID generation, and initial persistence.
- **Queue Service**: Manages the handoff to Redis-backed priority lanes.
- **Execution Factory**: Uses the Strategy pattern to resolve and execute specific job logic based on `jobType`.

### 3. Execution Engine (Workers)
- **Background Polling**: Autonomous worker threads that monitor High and Low priority queues.
- **Retry Logic**: Implements exponential backoff for transient failures.
- **DLQ Management**: Isolates terminal failures for manual intervention.

### 4. Storage & State
- **PostgreSQL**: The source of truth for job history, audit logs, and configuration state.
- **Redis**: The high-speed engine for priority queuing and rate-limiting state.

## Data Flow: Job Submission
1. Client sends request to `POST /api/jobs`.
2. Controller validates JSON structure and rate limits.
3. Service assigns a UUID and persists initial state to PostgreSQL.
4. Queue Service pushes the Job ID to the designated Redis Sorted Set.

## Data Flow: Job Execution
1. Background worker polls Redis for available jobs (High priority first).
2. Worker fetches full job parameters from PostgreSQL.
3. Strategy Executor executes the business logic.
4. Post-execution hooks update PostgreSQL with the result, message, and execution duration.

## Design Patterns
- **Strategy Pattern**: Decouples job execution logic from the scheduling engine.
- **Sliding Window**: Ensures fair usage through Redis-backed rate limiting.
- **Distributed Lock**: (Internal) Ensures job exclusivity across multi-node deployments.
