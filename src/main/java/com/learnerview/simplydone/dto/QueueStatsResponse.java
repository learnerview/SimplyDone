package com.learnerview.simplydone.dto;

import lombok.Builder;
import lombok.Data;

@Data @Builder
public class QueueStatsResponse {
    // Queue sizes (real-time Redis counts)
    private long highQueueSize;
    private long normalQueueSize;
    private long lowQueueSize;

    // Job counts by status (from DB)
    private long totalQueued;
    private long totalRunning;
    private long totalSuccess;
    private long totalFailed;
    private long totalDlq;

    // Enhanced metrics (computed on each stats call)
    private long totalProcessed;          // success + failed + dlq
    private double successRate;           // % of processed jobs that succeeded
    private double retryRate;             // % of processed jobs that needed ≥1 retry
    private double throughputPerMinute;   // successful completions in last 60 s
    private double avgLatencyMs;          // mean execution time of recent successes (ms)
}
