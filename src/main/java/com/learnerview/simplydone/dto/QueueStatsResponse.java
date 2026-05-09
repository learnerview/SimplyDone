package com.learnerview.simplydone.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class QueueStatsResponse {
    private long highQueueSize;
    private long normalQueueSize;
    private long lowQueueSize;

    private long totalQueued;
    private long totalRunning;
    private long totalSuccess;
    private long totalFailed;
    private long totalDlq;

    private long totalProcessed;
    private double successRate;
    private double retryRate;
    private double throughputPerMinute;
    private double avgLatencyMs;
}
