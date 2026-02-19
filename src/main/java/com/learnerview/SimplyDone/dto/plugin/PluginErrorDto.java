package com.learnerview.SimplyDone.dto.plugin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Enhanced error response DTO for plugin mode.
 * Provides detailed error information for host integration error handling.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PluginErrorDto {
    
    private String error;                          // Error type
    private String plugin;                         // Plugin name ("simplydone-scheduler")
    private String code;                           // Error code identifier
    private String message;                        // Human-readable message
    private LocalDateTime timestamp;               // Error occurrence time
    
    private String severity;                       // ERROR, WARNING, CRITICAL
    private String component;                      // Component that failed
    private String category;                       // Error category
    
    private ErrorDetails details;                  // Detailed error information
    private Map<String, Object> context;           // Additional context
    private String suggestion;                     // How to resolve
    
    /**
     * Detailed error information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorDetails {
        private String rootCause;                  // Root cause description
        private String[] stackTrace;               // Stack trace lines
        private String exceptionType;              // Java exception class
        private String exceptionMessage;           // Exception message
        private Integer httpStatus;                // HTTP status code
        private String retryable;                  // Retryable: true/false
    }
    
    /**
     * Error codes for plugin operations.
     */
    public static class ErrorCodes {
        // Plugin Configuration Errors
        public static final String PLUGIN_DISABLED = "PLUGIN_DISABLED";
        public static final String PLUGIN_NOT_CONFIGURED = "PLUGIN_NOT_CONFIGURED";
        public static final String INVALID_PLUGIN_MODE = "INVALID_PLUGIN_MODE";
        
        // Authentication Errors
        public static final String AUTH_FAILED = "AUTH_FAILED";
        public static final String MISSING_API_KEY = "MISSING_API_KEY";
        public static final String INVALID_API_KEY = "INVALID_API_KEY";
        public static final String UNAUTHORIZED = "UNAUTHORIZED";
        
        // Integration Errors
        public static final String HOST_INTEGRATION_FAILED = "HOST_INTEGRATION_FAILED";
        public static final String HOST_UNREACHABLE = "HOST_UNREACHABLE";
        public static final String HOST_TIMEOUT = "HOST_TIMEOUT";
        
        // Resource Errors
        public static final String RESOURCE_LIMIT_EXCEEDED = "RESOURCE_LIMIT_EXCEEDED";
        public static final String QUEUE_FULL = "QUEUE_FULL";
        public static final String OUT_OF_MEMORY = "OUT_OF_MEMORY";
        public static final String DISK_SPACE_LOW = "DISK_SPACE_LOW";
        
        // Database Errors
        public static final String DATABASE_ERROR = "DATABASE_ERROR";
        public static final String DATABASE_UNAVAILABLE = "DATABASE_UNAVAILABLE";
        public static final String SCHEMA_MISMATCH = "SCHEMA_MISMATCH";
        
        // Redis Errors
        public static final String REDIS_ERROR = "REDIS_ERROR";
        public static final String REDIS_UNAVAILABLE = "REDIS_UNAVAILABLE";
        public static final String QUEUE_ERROR = "QUEUE_ERROR";
        
        // Job Execution Errors
        public static final String JOB_EXECUTION_FAILED = "JOB_EXECUTION_FAILED";
        public static final String JOB_TIMEOUT = "JOB_TIMEOUT";
        public static final String INVALID_JOB_TYPE = "INVALID_JOB_TYPE";
        public static final String INVALID_JOB_PARAMETERS = "INVALID_JOB_PARAMETERS";
        
        // Validation Errors
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String CONFIGURATION_ERROR = "CONFIGURATION_ERROR";
        
        // Version Compatibility Errors
        public static final String VERSION_MISMATCH = "VERSION_MISMATCH";
        public static final String INCOMPATIBLE_HOST_VERSION = "INCOMPATIBLE_HOST_VERSION";
    }
    
    /**
     * Error severity levels.
     */
    public static class Severity {
        public static final String ERROR = "ERROR";
        public static final String WARNING = "WARNING";
        public static final String CRITICAL = "CRITICAL";
        public static final String INFO = "INFO";
    }
}
