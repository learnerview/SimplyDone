package com.learnerview.SimplyDone.dto.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Plugin information and status response DTO.
 * Provides metadata about the plugin service for host integration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PluginStatusDto {
    
    private String plugin;                          // Plugin name
    private String version;                         // Plugin version (e.g., "1.0.0")
    private String status;                          // Status: ACTIVE, MAINTENANCE, DEGRADED
    private LocalDateTime lastHealthCheck;          // Timestamp of last health check
    private String uptime;                          // Human-readable uptime
    
    private List<String> capabilities;              // List of supported job types
    private List<PluginEndpoint> endpoints;         // Available API endpoints
    private Map<String, String> metadata;           // Additional metadata
    
    private PluginDependencies dependencies;        // Database, Redis, etc.
    private PluginResourceUsage resourceUsage;      // Current resource utilization
    private PluginLimits limits;                    // Configured limits
    
    /**
     * Represents a single API endpoint exposed by the plugin.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PluginEndpoint {
        private String method;                      // HTTP method (GET, POST, etc.)
        private String path;                        // API path
        private String description;                 // Human-readable description
        private List<String> requiredPermissions;   // Required API permissions
    }
    
    /**
     * Plugin dependencies status.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PluginDependencies {
        private String database;                    // PostgreSQL connection status
        private String redis;                       // Redis connection status
        private String fileStorage;                 // File storage/disk status
    }
    
    /**
     * Current resource utilization metrics.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PluginResourceUsage {
        private Double cpuUsagePercent;             // CPU usage (0-100)
        private Double memoryUsageMb;               // Memory usage in MB
        private Double memoryUsagePercent;          // Memory usage (0-100)
        private Long diskUsageMb;                   // Disk usage in MB
        private Integer activeConnections;          // Active database connections
        private Integer queuedJobs;                 // Jobs waiting in queues
    }
    
    /**
     * Resource limits configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PluginLimits {
        private Integer maxQueueSize;               // Maximum jobs in queue
        private Integer maxConcurrentJobs;          // Max concurrent executions
        private Integer maxJobTimeoutSeconds;       // Job timeout limit
        private Integer memoryLimitMb;              // Memory limit
    }
}
