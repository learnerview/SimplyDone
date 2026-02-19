package com.learnerview.SimplyDone.config.plugin;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metrics configuration for plugin mode.
 * Adds plugin-specific metrics and tags for monitoring.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "simplydone.plugin.enabled", havingValue = "true")
public class PluginMetricsConfig {
    
    /**
     * Customize meter registry with plugin-specific tags.
     */
    @Bean
    public MeterRegistry.Config pluginMeterConfig(MeterRegistry meterRegistry) {
        log.info("Configuring plugin-specific metrics");
        
        meterRegistry.config().commonTags(
                Tags.of(
                        "plugin", "simplydone-scheduler",
                        "mode", "microservice",
                        "plugin.enabled", "true"
                )
        );
        
        return meterRegistry.config();
    }
    
    /**
     * Register custom plugin metrics.
     */
    @Bean
    public MeterBinder pluginMetricsBinder() {
        return registry -> {
            log.debug("Registering custom plugin metrics");
            
            // Plugin availability gauge
            registry.gauge("plugin.availability", Tags.empty(), 1);
            
            // Plugin uptime counter
            registry.counter("plugin.uptime.seconds", Tags.empty());
            
            // Job processing metrics
            registry.counter("plugin.jobs.submitted", Tags.of("priority", "high"));
            registry.counter("plugin.jobs.submitted", Tags.of("priority", "low"));
            registry.counter("plugin.jobs.executed.success", Tags.empty());
            registry.counter("plugin.jobs.executed.failed", Tags.empty());
            
            // Queue depth metrics
            registry.gauge(
                    "plugin.queue.depth",
                    Tags.of("queue", "high"),
                    0
            );
            
            registry.gauge(
                    "plugin.queue.depth",
                    Tags.of("queue", "low"),
                    0
            );
            
            registry.gauge(
                    "plugin.queue.depth",
                    Tags.of("queue", "dead-letter"),
                    0
            );
            
            // Resource metrics
            registry.gauge(
                    "plugin.memory.usage.percent",
                    Tags.empty(),
                    0d
            );
            
            registry.gauge(
                    "plugin.disk.usage.percent",
                    Tags.empty(),
                    0d
            );
            
            // Dependency health metrics
            registry.gauge(
                    "plugin.dependency.database.healthy",
                    Tags.empty(),
                    1
            );
            
            registry.gauge(
                    "plugin.dependency.redis.healthy",
                    Tags.empty(),
                    1
            );
            
            registry.gauge(
                    "plugin.dependency.storage.healthy",
                    Tags.empty(),
                    1
            );
            
            // API metrics
            registry.counter("plugin.api.requests", Tags.of("endpoint", "/api/plugin/status"));
            registry.counter("plugin.api.requests", Tags.of("endpoint", "/api/jobs"));
            
            registry.timer(
                    "plugin.api.response.time",
                    Tags.of("endpoint", "/api/jobs")
            );
        };
    }
}
