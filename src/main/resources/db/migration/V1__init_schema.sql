CREATE TABLE jobs (
    id VARCHAR(36) PRIMARY KEY,
    job_type VARCHAR(100) NOT NULL,
    producer VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    payload TEXT,
    result TEXT,
    next_run_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    visible_at TIMESTAMP(6) WITH TIME ZONE,
    lease_owner VARCHAR(100),
    lease_token VARCHAR(64),
    execution_type VARCHAR(20),
    execution_endpoint VARCHAR(1000),
    timeout_seconds INTEGER,
    callback_url VARCHAR(2000),
    started_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    attempt_count INTEGER NOT NULL,
    max_attempts INTEGER NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_jobs_producer_idempotency UNIQUE (producer, idempotency_key)
);

CREATE INDEX idx_status_next_run ON jobs(status, next_run_at);
CREATE INDEX idx_status_visible_at ON jobs(status, visible_at);
CREATE INDEX idx_job_type ON jobs(job_type);
CREATE INDEX idx_producer ON jobs(producer);

CREATE TABLE job_execution_logs (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    attempt INTEGER NOT NULL,
    status VARCHAR(20),
    message TEXT,
    duration_ms BIGINT,
    executed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
