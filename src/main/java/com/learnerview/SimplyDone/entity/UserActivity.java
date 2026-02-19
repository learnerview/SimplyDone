package com.learnerview.SimplyDone.entity;

import com.learnerview.SimplyDone.model.ActivityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for storing user activity logs in PostgreSQL.
 * Provides analytics and rate limiting support.
 */
@Entity
@Table(name = "user_activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private ActivityType activityType;
    
    @Column(nullable = false)
    private String resourceId; // Job ID, etc.
    
    @Column(columnDefinition = "TEXT")
    private String details; // JSON with additional details
    
    @Column(nullable = false)
    private Instant timestamp;
    
    @Column(nullable = false)
    private String ipAddress;
    
    @Column
    private String userAgent;
    
    @Column
    private Boolean success;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
