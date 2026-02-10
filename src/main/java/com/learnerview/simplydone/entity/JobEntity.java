package com.learnerview.simplydone.entity;

import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "jobs", indexes = {
        @Index(name = "idx_status_scheduled", columnList = "status, scheduledAt"),
        @Index(name = "idx_job_type", columnList = "jobType"),
        @Index(name = "idx_user_id", columnList = "userId"),
        @Index(name = "idx_workflow_id", columnList = "workflowId")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JobEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 100)
    private String jobType;

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

    @Column(length = 100)
    private String userId;

    @Column(nullable = false)
    private Instant scheduledAt;

    private Instant startedAt;
    private Instant completedAt;

    @Builder.Default
    private int attemptCount = 0;

    @Builder.Default
    private int maxRetries = 3;

    @Column(length = 36)
    private String workflowId;

    @Column(columnDefinition = "TEXT")
    private String dependsOn;

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
