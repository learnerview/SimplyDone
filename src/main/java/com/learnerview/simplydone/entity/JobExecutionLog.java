package com.learnerview.simplydone.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "job_execution_logs")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String jobId;

    private int attempt;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String message;

    private Long durationMs;

    @Column(nullable = false)
    private Instant executedAt;

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) executedAt = Instant.now();
    }
}
