package com.learnerview.simplydone.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
public class JobSubmissionRequest {
    @NotBlank(message = "jobType is required")
    private String jobType;
    private String priority;
    private Map<String, Object> payload;
    private String userId;
    private Instant scheduledAt;
    @Min(value = 0, message = "maxRetries must be >= 0")
    private Integer maxRetries;
}
