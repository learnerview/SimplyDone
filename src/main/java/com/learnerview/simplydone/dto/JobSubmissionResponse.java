package com.learnerview.simplydone.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data @Builder
public class JobSubmissionResponse {
    private String jobId;
    private String status;
    private String jobType;
    private String priority;
    private Instant scheduledAt;
}
