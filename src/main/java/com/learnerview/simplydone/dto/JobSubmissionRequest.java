package com.learnerview.simplydone.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class JobSubmissionRequest {
    @NotBlank(message = "jobType is required")
    private String jobType;

    @NotBlank(message = "producer is required")
    private String producer;

    @NotBlank(message = "idempotencyKey is required")
    private String idempotencyKey;

    private String priority;
    private Map<String, Object> payload;
    private Instant nextRunAt;

    @NotNull(message = "execution is required")
    private ExecutionRequest execution;

    @Min(value = 1, message = "maxAttempts must be >= 1")
    private Integer maxAttempts;

    @Min(value = 1, message = "timeoutSeconds must be >= 1")
    private Integer timeoutSeconds;

    private String callbackUrl;

    @Data
    public static class ExecutionRequest {
        @NotBlank(message = "execution.type is required")
        private String type;

        @NotBlank(message = "execution.endpoint is required")
        private String endpoint;
    }
}
