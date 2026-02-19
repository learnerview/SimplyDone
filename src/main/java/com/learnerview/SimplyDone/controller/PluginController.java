package com.learnerview.SimplyDone.controller;

import com.learnerview.SimplyDone.dto.plugin.*;
import com.learnerview.SimplyDone.service.impl.PluginServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Plugin Integration Controller.
 * Provides plugin-specific endpoints for microservice integration.
 * Only active when simplydone.plugin.enabled=true
 */
@Slf4j
@RestController
@RequestMapping("/api/plugin")
@ConditionalOnProperty(name = "simplydone.plugin.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PluginController {
    
    private final PluginServiceImpl pluginService;
    
    /**
     * GET /api/plugin/status
     * Returns plugin information and capabilities for host integration.
     * 
     * @return Plugin status with capabilities and endpoints
     */
    @GetMapping("/status")
    public ResponseEntity<PluginStatusDto> getPluginStatus() {
        log.debug("Plugin status check requested");
        
        try {
            PluginStatusDto status = pluginService.getPluginStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error retrieving plugin status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /api/plugin/health/detailed
     * Returns detailed health information for plugin dependencies.
     * 
     * @return Detailed health status of database, Redis, storage, and worker
     */
    @GetMapping("/health/detailed")
    public ResponseEntity<PluginHealthDto> getDetailedHealth() {
        log.debug("Detailed health check requested");
        
        try {
            PluginHealthDto health = pluginService.getDetailedHealth();
            
            // Return appropriate status code based on health
            if ("DOWN".equals(health.getStatus())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
            } else if ("DEGRADED".equals(health.getStatus())) {
                return ResponseEntity.status(HttpStatus.ACCEPTED).body(health);
            }
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error retrieving detailed health", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /api/plugin/health/database
     * Returns database-specific health information.
     * 
     * @return Database health status
     */
    @GetMapping("/health/database")
    public ResponseEntity<PluginHealthDto.DatabaseHealth> getDatabaseHealth() {
        log.debug("Database health check requested");
        
        try {
            PluginHealthDto.DatabaseHealth dbHealth = pluginService.getDatabaseHealth();
            
            if ("DOWN".equals(dbHealth.getStatus())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(dbHealth);
            }
            
            return ResponseEntity.ok(dbHealth);
        } catch (Exception e) {
            log.error("Error retrieving database health", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /api/plugin/health/cache
     * Returns Redis cache health information.
     * 
     * @return Redis health status
     */
    @GetMapping("/health/cache")
    public ResponseEntity<PluginHealthDto.CacheHealth> getCacheHealth() {
        log.debug("Cache health check requested");
        
        try {
            PluginHealthDto.CacheHealth cacheHealth = pluginService.getCacheHealth();
            
            if ("DOWN".equals(cacheHealth.getStatus())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(cacheHealth);
            }
            
            return ResponseEntity.ok(cacheHealth);
        } catch (Exception e) {
            log.error("Error retrieving cache health", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * POST /api/plugin/validate-config
     * Validates plugin configuration against host requirements.
     * 
     * @return Configuration validation results with any issues found
     */
    @PostMapping("/validate-config")
    public ResponseEntity<ConfigValidationDto> validateConfiguration() {
        log.debug("Configuration validation requested");
        
        try {
            ConfigValidationDto validation = pluginService.validateConfiguration();
            
            if (!validation.getValid()) {
                // Configuration is invalid but return 200 with details
                log.warn("Configuration validation failed with {} issues", 
                        validation.getIssues().size());
            }
            
            return ResponseEntity.ok(validation);
        } catch (Exception e) {
            log.error("Error validating configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /api/plugin/version
     * Returns plugin version and compatibility information.
     * 
     * @return Version information
     */
    @GetMapping("/version")
    public ResponseEntity<?> getVersion() {
        log.debug("Version information requested");
        
        try {
            return ResponseEntity.ok(pluginService.getVersionInfo());
        } catch (Exception e) {
            log.error("Error retrieving version information", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /api/plugin/capabilities
     * Returns list of supported capabilities and job types.
     * 
     * @return List of capabilities
     */
    @GetMapping("/capabilities")
    public ResponseEntity<?> getCapabilities() {
        log.debug("Capabilities request");
        
        try {
            return ResponseEntity.ok(pluginService.getCapabilities());
        } catch (Exception e) {
            log.error("Error retrieving capabilities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /api/plugin/metrics
     * Returns plugin-specific metrics and statistics.
     * 
     * @return Plugin metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        log.debug("Metrics request");
        
        try {
            return ResponseEntity.ok(pluginService.getPluginMetrics());
        } catch (Exception e) {
            log.error("Error retrieving metrics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * GET /api/plugin/config
     * Returns current public configuration settings.
     * (Sensitive values like API keys are excluded)
     * 
     * @return Public configuration
     */
    @GetMapping("/config")
    public ResponseEntity<?> getConfiguration() {
        log.debug("Configuration request");
        
        try {
            return ResponseEntity.ok(pluginService.getPublicConfiguration());
        } catch (Exception e) {
            log.error("Error retrieving configuration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * POST /api/plugin/init
     * Initializes plugin resources and verifies connectivity.
     * Used by host after first deployment.
     * 
     * @return Initialization result
     */
    @PostMapping("/init")
    public ResponseEntity<?> initializePlugin() {
        log.info("Plugin initialization requested");
        
        try {
            return ResponseEntity.ok(pluginService.initializePlugin());
        } catch (Exception e) {
            log.error("Error during plugin initialization", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * POST /api/plugin/shutdown
     * Graceful shutdown of plugin services.
     * 
     * @return Shutdown confirmation
     */
    @PostMapping("/shutdown")
    public ResponseEntity<?> shutdownPlugin() {
        log.info("Plugin shutdown requested");
        
        try {
            return ResponseEntity.accepted().body(pluginService.shutdownPlugin());
        } catch (Exception e) {
            log.error("Error during plugin shutdown", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Error handler for plugin controller exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<PluginErrorDto> handleException(Exception e) {
        log.error("Plugin controller exception", e);
        
        PluginErrorDto errorResponse = PluginErrorDto.builder()
                .error("PLUGIN_ERROR")
                .plugin("simplydone-scheduler")
                .code(PluginErrorDto.ErrorCodes.PLUGIN_NOT_CONFIGURED)
                .message(e.getMessage())
                .severity(PluginErrorDto.Severity.ERROR)
                .component("PluginController")
                .suggestion("Check plugin logs for more details")
                .build();
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
