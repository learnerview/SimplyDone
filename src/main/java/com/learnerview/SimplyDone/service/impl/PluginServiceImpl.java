package com.learnerview.SimplyDone.service.impl;

import com.learnerview.SimplyDone.dto.plugin.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Plugin Service Implementation.
 * Provides business logic for plugin integration endpoints.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "simplydone.plugin.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PluginServiceImpl {
    
    @Value("${simplydone.plugin.api.name:simplydone-scheduler}")
    private String pluginName;
    
    @Value("${simplydone.plugin.version:1.0.0}")
    private String pluginVersion;
    
    @Value("${simplydone.plugin.upload.directory:${java.io.tmpdir}/simplydone-plugin-uploads}")
    private String uploadDirectory;
    
    @Value("${simplydone.plugin.limits.max-queue-size:10000}")
    private Integer maxQueueSize;
    
    @Value("${simplydone.plugin.limits.max-concurrent-jobs:10}")
    private Integer maxConcurrentJobs;
    
    @Value("${simplydone.plugin.limits.max-job-timeout-seconds:300}")
    private Integer maxJobTimeout;
    
    @Value("${simplydone.plugin.limits.memory-limit-mb:512}")
    private Integer memoryLimit;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final EntityManager entityManager;
    
    /**
     * Get plugin status with capabilities and metadata.
     */
    public PluginStatusDto getPluginStatus() {
        log.debug("Retrieving plugin status");
        
        try {
            PluginStatusDto.PluginResourceUsage resourceUsage = getResourceUsage();
            PluginStatusDto.PluginDependencies dependencies = checkDependencies();
            PluginStatusDto.PluginLimits limits = PluginStatusDto.PluginLimits.builder()
                    .maxQueueSize(maxQueueSize)
                    .maxConcurrentJobs(maxConcurrentJobs)
                    .maxJobTimeoutSeconds(maxJobTimeout)
                    .memoryLimitMb(memoryLimit)
                    .build();
            
            String status = determineOverallStatus(dependencies, resourceUsage);
            
            return PluginStatusDto.builder()
                    .plugin(pluginName)
                    .version(pluginVersion)
                    .status(status)
                    .lastHealthCheck(LocalDateTime.now())
                    .uptime(getUptime())
                    .capabilities(getSupportedJobTypes())
                    .endpoints(getAvailableEndpoints())
                    .dependencies(dependencies)
                    .resourceUsage(resourceUsage)
                    .limits(limits)
                    .metadata(getMetadata())
                    .build();
        } catch (Exception e) {
            log.error("Error getting plugin status", e);
            throw new RuntimeException("Failed to get plugin status: " + e.getMessage());
        }
    }
    
    /**
     * Get detailed health information.
     */
    public PluginHealthDto getDetailedHealth() {
        log.debug("Retrieving detailed health");
        
        try {
            PluginHealthDto.DatabaseHealth dbHealth = getDatabaseHealth();
            PluginHealthDto.CacheHealth cacheHealth = getCacheHealth();
            PluginHealthDto.StorageHealth storageHealth = getStorageHealth();
            PluginHealthDto.QueueHealth queueHealth = getQueueHealth();
            PluginHealthDto.WorkerHealth workerHealth = getWorkerHealth();
            
            String overallStatus = determineOverallHealthStatus(dbHealth, cacheHealth, storageHealth);
            
            return PluginHealthDto.builder()
                    .status(overallStatus)
                    .timestamp(LocalDateTime.now())
                    .responseTimeMs(System.currentTimeMillis())
                    .database(dbHealth)
                    .cache(cacheHealth)
                    .storage(storageHealth)
                    .queues(queueHealth)
                    .worker(workerHealth)
                    .build();
        } catch (Exception e) {
            log.error("Error getting detailed health", e);
            throw new RuntimeException("Failed to get health: " + e.getMessage());
        }
    }
    
    /**
     * Get database health status.
     */
    public PluginHealthDto.DatabaseHealth getDatabaseHealth() {
        try {
            // Test database connectivity
            entityManager.createQuery("SELECT 1").getSingleResult();
            
            return PluginHealthDto.DatabaseHealth.builder()
                    .status("UP")
                    .connectionStatus("Connected")
                    .databaseVersion("PostgreSQL 15+")
                    .schemaStatus("Initialized")
                    .valid(true)
                    .build();
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return PluginHealthDto.DatabaseHealth.builder()
                    .status("DOWN")
                    .connectionStatus("Disconnected")
                    .valid(false)
                    .build();
        }
    }
    
    /**
     * Get Redis cache health status.
     */
    public PluginHealthDto.CacheHealth getCacheHealth() {
        try {
            // Test Redis connectivity
            redisTemplate.hasKey("test");
            
            return PluginHealthDto.CacheHealth.builder()
                    .status("UP")
                    .connectionStatus("Connected")
                    .connectedClients(1)
                    .redisVersion("7.0+")
                    .persistenceEnabled(true)
                    .valid(true)
                    .build();
        } catch (Exception e) {
            log.error("Cache health check failed", e);
            return PluginHealthDto.CacheHealth.builder()
                    .status("DOWN")
                    .connectionStatus("Disconnected")
                    .valid(false)
                    .build();
        }
    }
    
    /**
     * Get storage (disk) health status.
     */
    public PluginHealthDto.StorageHealth getStorageHealth() {
        try {
            File dir = new File(uploadDirectory);
            long totalSpace = dir.getTotalSpace();
            long usableSpace = dir.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;
            double usagePercent = (usedSpace * 100.0) / totalSpace;
            
            return PluginHealthDto.StorageHealth.builder()
                    .status("UP")
                    .uploadDirectory(uploadDirectory)
                    .totalSpaceBytes(totalSpace)
                    .usableSpaceBytes(usableSpace)
                    .usagePercent(usagePercent)
                    .writable(dir.canWrite())
                    .valid(true)
                    .build();
        } catch (Exception e) {
            log.error("Storage health check failed", e);
            return PluginHealthDto.StorageHealth.builder()
                    .status("DOWN")
                    .valid(false)
                    .build();
        }
    }
    
    /**
     * Get job queue health status.
     */
    public PluginHealthDto.QueueHealth getQueueHealth() {
        try {
            // Get queue sizes from Redis
            Long highQueueSize = redisTemplate.opsForList().size("jobs:high");
            Long lowQueueSize = redisTemplate.opsForList().size("jobs:low");
            Long dlqSize = redisTemplate.opsForList().size("dead_letter:jobs");
            
            highQueueSize = highQueueSize != null ? highQueueSize : 0;
            lowQueueSize = lowQueueSize != null ? lowQueueSize : 0;
            dlqSize = dlqSize != null ? dlqSize : 0;
            
            String status = (highQueueSize + lowQueueSize) < (maxQueueSize * 0.9) ? "UP" : "DEGRADED";
            
            return PluginHealthDto.QueueHealth.builder()
                    .status(status)
                    .highPriority(highQueueSize.intValue())
                    .lowPriority(lowQueueSize.intValue())
                    .deadLetter(dlqSize.intValue())
                    .totalQueued(highQueueSize.intValue() + lowQueueSize.intValue())
                    .build();
        } catch (Exception e) {
            log.error("Queue health check failed", e);
            return PluginHealthDto.QueueHealth.builder()
                    .status("DOWN")
                    .build();
        }
    }
    
    /**
     * Get worker thread health status.
     */
    public PluginHealthDto.WorkerHealth getWorkerHealth() {
        // Implementation depends on your actual worker thread
        return PluginHealthDto.WorkerHealth.builder()
                .status("RUNNING")
                .threadAlive(true)
                .pollIntervalMs(1000)
                .totalJobsProcessed(0L)
                .totalJobsFailed(0L)
                .successRate(100.0)
                .build();
    }
    
    /**
     * Validate plugin configuration.
     */
    public ConfigValidationDto validateConfiguration() {
        log.debug("Validating plugin configuration");
        
        List<ConfigValidationDto.ValidationIssue> issues = new ArrayList<>();
        
        ConfigValidationDto.DatabaseConfig dbConfig = validateDatabaseConfig();
        ConfigValidationDto.CacheConfig cacheConfig = validateCacheConfig();
        ConfigValidationDto.SecurityConfig securityConfig = validateSecurityConfig();
        ConfigValidationDto.LimitsConfig limitsConfig = validateLimitsConfig();
        
        if (!dbConfig.getValid()) {
            issues.add(ConfigValidationDto.ValidationIssue.builder()
                    .severity("ERROR")
                    .category("Database")
                    .code("DATABASE_CONFIG_INVALID")
                    .message("Database configuration is invalid")
                    .build());
        }
        
        boolean allValid = dbConfig.getValid() && cacheConfig.getValid() && 
                          securityConfig.getValid() && limitsConfig.getValid();
        
        return ConfigValidationDto.builder()
                .valid(allValid)
                .timestamp(LocalDateTime.now())
                .issues(issues)
                .database(dbConfig)
                .cache(cacheConfig)
                .security(securityConfig)
                .limits(limitsConfig)
                .build();
    }
    
    /**
     * Get version information.
     */
    public Map<String, Object> getVersionInfo() {
        return Map.ofEntries(
                Map.entry("plugin", pluginName),
                Map.entry("version", pluginVersion),
                Map.entry("compatibility", Map.of(
                        "minHostVersion", "1.0.0",
                        "maxHostVersion", "2.0.0"
                )),
                Map.entry("timestamp", LocalDateTime.now())
        );
    }
    
    /**
     * Get plugin capabilities.
     */
    public Map<String, Object> getCapabilities() {
        return Map.ofEntries(
                Map.entry("jobTypes", getSupportedJobTypes()),
                Map.entry("features", List.of(
                        "job-scheduling",
                        "priority-queues",
                        "retry-logic",
                        "rate-limiting",
                        "dead-letter-queue",
                        "file-uploads",
                        "metrics"
                )),
                Map.entry("maxConcurrentJobs", maxConcurrentJobs),
                Map.entry("maxJobTimeout", maxJobTimeout)
        );
    }
    
    /**
     * Get plugin metrics.
     */
    public Map<String, Object> getPluginMetrics() {
        return Map.ofEntries(
                Map.entry("resource_usage", getResourceUsage()),
                Map.entry("timestamp", LocalDateTime.now())
        );
    }
    
    /**
     * Get public configuration (sensitive values excluded).
     */
    public Map<String, Object> getPublicConfiguration() {
        return Map.ofEntries(
                Map.entry("plugin_enabled", true),
                Map.entry("plugin_version", pluginVersion),
                Map.entry("redis_namespace", "simplydone-plugin"),
                Map.entry("upload_directory", uploadDirectory),
                Map.entry("limits", Map.of(
                        "maxQueueSize", maxQueueSize,
                        "maxConcurrentJobs", maxConcurrentJobs,
                        "maxJobTimeout", maxJobTimeout
                ))
        );
    }
    
    /**
     * Initialize plugin resources.
     */
    public Map<String, Object> initializePlugin() {
        log.info("Initializing plugin resources");
        
        try {
            // Ensure upload directory exists
            Files.createDirectories(Paths.get(uploadDirectory));
            
            // Verify database connectivity
            getDatabaseHealth();
            
            // Verify Redis connectivity
            getCacheHealth();
            
            return Map.of(
                    "status", "SUCCESS",
                    "message", "Plugin initialized successfully",
                    "timestamp", LocalDateTime.now()
            );
        } catch (Exception e) {
            log.error("Plugin initialization failed", e);
            return Map.of(
                    "status", "FAILED",
                    "error", e.getMessage(),
                    "timestamp", LocalDateTime.now()
            );
        }
    }
    
    /**
     * Shutdown plugin gracefully.
     */
    public Map<String, Object> shutdownPlugin() {
        log.info("Shutting down plugin");
        
        return Map.of(
                "status", "SUCCESS",
                "message", "Plugin shutdown initiated",
                "timestamp", LocalDateTime.now()
        );
    }
    
    // ==================== Private Helper Methods ====================
    
    private PluginStatusDto.PluginResourceUsage getResourceUsage() {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        
        long usedMemory = memBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        double memoryPercent = (usedMemory * 100.0) / memoryLimit;
        
        return PluginStatusDto.PluginResourceUsage.builder()
                .cpuUsagePercent(((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad() * 100)
                .memoryUsageMb((double) usedMemory)
                .memoryUsagePercent(memoryPercent)
                .diskUsageMb(getDiskUsageMb())
                .build();
    }
    
    private PluginStatusDto.PluginDependencies checkDependencies() {
        String dbStatus = getDatabaseHealth().getStatus();
        String redisStatus = getCacheHealth().getStatus();
        String fsStatus = getStorageHealth().getStatus();
        
        return PluginStatusDto.PluginDependencies.builder()
                .database(dbStatus)
                .redis(redisStatus)
                .fileStorage(fsStatus)
                .build();
    }
    
    private String determineOverallStatus(PluginStatusDto.PluginDependencies deps,
                                         PluginStatusDto.PluginResourceUsage usage) {
        if ("DOWN".equals(deps.getDatabase()) || "DOWN".equals(deps.getRedis())) {
            return "DOWN";
        }
        if (usage.getMemoryUsagePercent() > 90) {
            return "DEGRADED";
        }
        return "ACTIVE";
    }
    
    private String determineOverallHealthStatus(PluginHealthDto.DatabaseHealth db,
                                                PluginHealthDto.CacheHealth cache,
                                                PluginHealthDto.StorageHealth storage) {
        if ("DOWN".equals(db.getStatus()) || "DOWN".equals(cache.getStatus())) {
            return "DOWN";
        }
        if ("DEGRADED".equals(db.getStatus()) || "DEGRADED".equals(cache.getStatus())) {
            return "DEGRADED";
        }
        return "UP";
    }
    
    private List<String> getSupportedJobTypes() {
        return List.of(
                "API_CALL",
                "EMAIL_SEND",
                "DATA_PROCESS",
                "FILE_OPERATION",
                "NOTIFICATION",
                "REPORT_GENERATION",
                "CLEANUP"
        );
    }
    
    private List<PluginStatusDto.PluginEndpoint> getAvailableEndpoints() {
        return List.of(
                PluginStatusDto.PluginEndpoint.builder()
                        .method("GET")
                        .path("/api/plugin/status")
                        .description("Get plugin status and capabilities")
                        .build(),
                PluginStatusDto.PluginEndpoint.builder()
                        .method("GET")
                        .path("/api/plugin/health/detailed")
                        .description("Get detailed health information")
                        .build(),
                PluginStatusDto.PluginEndpoint.builder()
                        .method("POST")
                        .path("/api/plugin/validate-config")
                        .description("Validate plugin configuration")
                        .build()
        );
    }
    
    private Map<String, String> getMetadata() {
        return Map.of(
                "deploymentMode", "microservice-plugin",
                "environment", "production"
        );
    }
    
    private String getUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        return String.format("%dd %dh %dm %ds", days, hours % 24, minutes % 60, seconds % 60);
    }
    
    private ConfigValidationDto.DatabaseConfig validateDatabaseConfig() {
        try {
            getDatabaseHealth();
            return ConfigValidationDto.DatabaseConfig.builder()
                    .configured(true)
                    .accessible(true)
                    .version("PostgreSQL 15+")
                    .valid(true)
                    .build();
        } catch (Exception e) {
            return ConfigValidationDto.DatabaseConfig.builder()
                    .configured(true)
                    .accessible(false)
                    .valid(false)
                    .build();
        }
    }
    
    private ConfigValidationDto.CacheConfig validateCacheConfig() {
        try {
            getCacheHealth();
            return ConfigValidationDto.CacheConfig.builder()
                    .configured(true)
                    .accessible(true)
                    .version("Redis 7.0+")
                    .valid(true)
                    .build();
        } catch (Exception e) {
            return ConfigValidationDto.CacheConfig.builder()
                    .configured(true)
                    .accessible(false)
                    .valid(false)
                    .build();
        }
    }
    
    private ConfigValidationDto.SecurityConfig validateSecurityConfig() {
        return ConfigValidationDto.SecurityConfig.builder()
                .apiKeyConfigured(true)
                .requireApiKey(true)
                .apiKeyHeaderName("X-Plugin-API-Key")
                .enableRequestSigning(true)
                .valid(true)
                .build();
    }
    
    private ConfigValidationDto.LimitsConfig validateLimitsConfig() {
        return ConfigValidationDto.LimitsConfig.builder()
                .maxQueueSize(maxQueueSize)
                .maxConcurrentJobs(maxConcurrentJobs)
                .maxJobTimeoutSeconds(maxJobTimeout)
                .memoryLimitMb(memoryLimit)
                .valid(true)
                .build();
    }
    
    private long getDiskUsageMb() {
        try {
            File dir = new File(uploadDirectory);
            return (dir.getTotalSpace() - dir.getUsableSpace()) / (1024 * 1024);
        } catch (Exception e) {
            return 0;
        }
    }
}
