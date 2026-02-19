package com.learnerview.SimplyDone.entity;

import com.learnerview.SimplyDone.model.ExecutionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for storing job execution logs in PostgreSQL.
 * Provides audit trail and performance metrics.
 */
@Entity
@Table(name = "job_execution_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String jobId;
    
    @Column(nullable = false)
    private String jobType;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private Instant startedAt;
    
    @Column
    private Instant completedAt;
    
    @Column
    private Long executionTimeMs;
    
    @Column(nullable = false)
    private ExecutionStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(columnDefinition = "TEXT")
    private String executionResult;
    
    @Column
    private String workerId;
    
    @Column
    private Integer attemptNumber;
    
    @Column(columnDefinition = "TEXT")
    private String systemInfo; // JSON with CPU, memory info
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
