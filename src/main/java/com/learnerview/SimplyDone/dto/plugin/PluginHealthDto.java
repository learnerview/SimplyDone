package com.learnerview.SimplyDone.dto.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Detailed health status DTO for plugin mode.
 * Provides comprehensive health information for monitoring and alerting.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PluginHealthDto {
    
    private String status;                         // Overall status: UP, DOWN, DEGRADED
    private LocalDateTime timestamp;               // Health check timestamp
    private Long responseTimeMs;                   // Time to compute health check
    
    private DatabaseHealth database;               // PostgreSQL health
    private CacheHealth cache;                     // Redis health
    private StorageHealth storage;                 // File storage health
    
    private QueueHealth queues;                    // Job queue status
    private WorkerHealth worker;                   // Background worker status
    
    private Map<String, Object> additionalChecks;  // Custom health checks
    
    /**
     * PostgreSQL database health status.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DatabaseHealth {
        private String status;                     // UP, DOWN, DEGRADED
        private String connectionStatus;           // Connected/Disconnected
        private Integer activeConnections;         // Current connection count
        private Integer maxConnections;            // Connection pool limit
        private Long responseTimeMs;               // Query response time
        private String databaseVersion;            // PostgreSQL version
        private String schemaStatus;               // Schema initialization status
        private Boolean valid;                     // Health status bit
    }
    
    /**
     * Redis cache health status.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CacheHealth {
        private String status;                     // UP, DOWN, DEGRADED
        private String connectionStatus;           // Connected/Disconnected
        private Integer connectedClients;          // Connected client count
        private Long usedMemoryBytes;              // Memory usage
        private Long maxMemoryBytes;               // Max memory limit
        private Double memoryUsagePercent;         // Percentage used
        private String redisVersion;               // Redis version
        private Boolean persistenceEnabled;        // Persistence status
        private Boolean valid;                     // Health status bit
    }
    
    /**
     * File storage health status.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StorageHealth {
        private String status;                     // UP, DOWN, DEGRADED
        private String uploadDirectory;            // Upload directory path
        private Long totalSpaceBytes;              // Total disk space
        private Long usableSpaceBytes;             // Available space
        private Double usagePercent;               // Space usage percentage
        private Boolean writable;                  // Can write to directory
        private Integer uploadedFileCount;         // Number of uploaded files
        private Boolean valid;                     // Health status bit
    }
    
    /**
     * Job queue health status.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QueueHealth {
        private String status;                     // UP, DOWN, DEGRADED
        private Integer highPriority;              // Jobs in HIGH priority queue
        private Integer lowPriority;               // Jobs in LOW priority queue
        private Integer deadLetter;                // Jobs in dead letter queue
        private Integer totalQueued;               // Total queued jobs
        private Double averageQueueWaitSeconds;    // Avg time jobs wait
        private Long maxQueuedJobAgeSeconds;       // Age of oldest queued job
    }
    
    /**
     * Background worker thread health status.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WorkerHealth {
        private String status;                     // RUNNING, STOPPED, ERROR
        private Boolean threadAlive;               // Is worker thread alive
        private Long lastPollTimestamp;            // Last poll time (epoch ms)
        private Long nextScheduledPollMs;          // Next poll in ms
        private Integer pollIntervalMs;            // Poll frequency
        private Long totalJobsProcessed;           // Total jobs executed
        private Long totalJobsFailed;              // Total failed jobs
        private Double successRate;                // Success percentage (0-100)
    }
}
