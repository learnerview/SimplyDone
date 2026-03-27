package com.learnerview.simplydone.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.Map;

@Data @Builder
public class JobResponse {
    private String id;
    private String jobType;
    private String producer;
    private String idempotencyKey;
    private String status;
    private String priority;
    private Map<String, Object> payload;
    private String result;

    private Instant nextRunAt;
    private Instant visibleAt;
    private String leaseOwner;

    private String executionType;
    private String executionEndpoint;

    private Integer timeoutSeconds;
    private String callbackUrl;

    private Instant startedAt;
    private Instant completedAt;
    private int attemptCount;
    private int maxAttempts;
    private Instant createdAt;
    private Instant updatedAt;
}
