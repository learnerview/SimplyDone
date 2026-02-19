package com.learnerview.SimplyDone.dto.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Configuration validation response DTO for plugin mode.
 * Validates plugin configuration against host requirements.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigValidationDto {
    
    private Boolean valid;                         // Overall validity
    private LocalDateTime timestamp;               // Validation time
    private List<ValidationIssue> issues;          // Found issues (warnings, errors)
    
    private DatabaseConfig database;               // Database configuration status
    private CacheConfig cache;                     // Redis configuration status
    private SecurityConfig security;               // Security configuration status
    private LimitsConfig limits;                   // Resource limits status
    
    private Map<String, Object> metadata;          // Additional validation info
    
    /**
     * Represents a single validation issue.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidationIssue {
        private String severity;                   // ERROR, WARNING, INFO
        private String category;                   // Configuration category
        private String code;                       // Issue identifier
        private String message;                    // Human-readable message
        private String suggestion;                 // How to fix
        private Map<String, Object> context;       // Additional details
    }
    
    /**
     * Database configuration validation.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DatabaseConfig {
        private Boolean configured;                // Is configured
        private Boolean accessible;                // Can connect
        private String version;                    // PostgreSQL version
        private String schemaStatus;               // Schema exists/missing
        private Integer connectionPoolSize;        // Current pool size
        private Boolean valid;                     // Overall validity
    }
    
    /**
     * Cache (Redis) configuration validation.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CacheConfig {
        private Boolean configured;                // Is configured
        private Boolean accessible;                // Can connect
        private String version;                    // Redis version
        private Long availableMemoryBytes;         // Available memory
        private Boolean persistenceEnabled;        // RDB/AOF enabled
        private Boolean valid;                     // Overall validity
    }
    
    /**
     * Security configuration validation.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SecurityConfig {
        private Boolean apiKeyConfigured;          // API key set
        private Boolean requireApiKey;             // API key required
        private String apiKeyHeaderName;           // Header name for key
        private Boolean enableRequestSigning;      // HMAC signing enabled
        private Boolean valid;                     // Overall validity
    }
    
    /**
     * Resource limits configuration validation.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LimitsConfig {
        private Integer maxQueueSize;              // Queue size limit
        private Integer maxConcurrentJobs;         // Concurrent job limit
        private Integer maxJobTimeoutSeconds;      // Job timeout limit
        private Integer memoryLimitMb;             // Memory limit
        private Boolean valid;                     // Overall validity
    }
}
