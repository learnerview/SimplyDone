package com.learnerview.SimplyDone.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Configuration class for custom metrics and health indicators.
 * 
 * This class provides:
 * - Custom metrics for job processing
 * - Health indicators for Redis connectivity
 * - Timer metrics for job execution
 */
@Configuration
@RequiredArgsConstructor
public class MetricsConfig {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * Custom health indicator for Redis connectivity.
     * 
     * @return health status based on Redis connection
     */
    @Bean
    public HealthIndicator redisHealthIndicator() {
        return () -> {
            try {
                // Test Redis connectivity
                redisTemplate.opsForValue().get("health:check");
                return Health.up()
                        .withDetail("service", "Redis")
                        .withDetail("status", "Connected")
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("service", "Redis")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * Timer for measuring job execution time.
     * 
     * @param registry the meter registry
     * @return timer for job execution
     */
    @Bean
    public Timer jobExecutionTimer(MeterRegistry registry) {
        return Timer.builder("job.execution.time")
                .description("Time taken to execute jobs")
                .tag("application", "SimplyDone")
                .register(registry);
    }

    /**
     * Timer for measuring job submission time.
     * 
     * @param registry the meter registry
     * @return timer for job submission
     */
    @Bean
    public Timer jobSubmissionTimer(MeterRegistry registry) {
        return Timer.builder("job.submission.time")
                .description("Time taken to submit jobs")
                .tag("application", "SimplyDone")
                .register(registry);
    }
}
