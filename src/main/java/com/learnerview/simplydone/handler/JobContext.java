package com.learnerview.simplydone.handler;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.Map;

@Getter @Builder
public class JobContext {
    private final String jobId;
    private final String jobType;
    private final Map<String, Object> payload;
    private final int attemptNumber;
    private final Instant scheduledAt;
    private final String userId;
}
