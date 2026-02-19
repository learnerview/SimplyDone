package com.learnerview.SimplyDone.entity;

import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobStatus;
import com.learnerview.SimplyDone.model.JobType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for storing job information in PostgreSQL.
 * Provides persistent storage and audit capabilities.
 */
@Entity
@Table(name = "jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private JobType jobType;
    
    @Column(nullable = false)
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobPriority priority;
    
    @Column(nullable = false)
    private int delaySeconds;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(columnDefinition = "TEXT")
    private String parameters; // JSON string
    
    @Column(nullable = false)
    private int timeoutSeconds;
    
    @Column
    private Integer maxRetries;
    
    @Column(columnDefinition = "TEXT")
    private String dependencies; // JSON array
    
    @Column(columnDefinition = "TEXT")
    private String tags; // JSON array
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON object
    
    @Column(nullable = false)
    private Instant submittedAt;
    
    @Column(nullable = false)
    private Instant executeAt;
    
    @Column
    private Instant executedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;
    
    @Column(nullable = false)
    private int attemptCount;
    
    @Column(columnDefinition = "TEXT")
    private String executionResult;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
