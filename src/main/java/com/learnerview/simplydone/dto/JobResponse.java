package com.learnerview.simplydone.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

@Data @Builder
public class JobResponse {
    private String id;
    private String jobType;
    private String status;
    private String priority;
    private Map<String, Object> payload;
    private String result;
    private String userId;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant completedAt;
    private int attemptCount;
    private int maxRetries;
    private String workflowId;
    private String dependsOn;
    private Instant createdAt;
    private Instant updatedAt;
}
