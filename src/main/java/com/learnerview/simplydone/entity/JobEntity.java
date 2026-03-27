package com.learnerview.simplydone.entity;

import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "jobs", indexes = {
    @Index(name = "idx_status_next_run", columnList = "status, nextRunAt"),
    @Index(name = "idx_status_visible_at", columnList = "status, visibleAt"),
        @Index(name = "idx_job_type", columnList = "jobType"),
    @Index(name = "idx_producer", columnList = "producer")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_jobs_producer_idempotency", columnNames = {"producer", "idempotency_key"})
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JobEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String jobType;

    @Column(nullable = false, length = 120)
    private String producer;

    @Column(name = "idempotency_key", nullable = false, length = 150)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobPriority priority;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(nullable = false)
    private Instant nextRunAt;

    private Instant visibleAt;

    @Column(length = 100)
    private String leaseOwner;

    @Column(length = 64)
    private String leaseToken;

    @Column(length = 20)
    private String executionType;

    @Column(length = 1000)
    private String executionEndpoint;

    private Integer timeoutSeconds;

    @Column(length = 2000)
    private String callbackUrl;

    private Instant startedAt;
    private Instant completedAt;

    @Builder.Default
    private int attemptCount = 0;

    @Builder.Default
    private int maxAttempts = 3;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
