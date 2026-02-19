package com.learnerview.SimplyDone.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application metrics using Micrometer.
 */
@Configuration
public class MetricsConfig {

    /**
     * Creates a Timer bean for measuring job execution time.
     * This timer is used to track performance metrics for job processing.
     *
     * @param registry The meter registry for storing metrics
     * @return Timer instance for job execution metrics
     */
    @Bean
    public Timer jobExecutionTimer(MeterRegistry registry) {
        return Timer.builder("job.execution.time")
                .description("Time taken to execute jobs")
                .tag("component", "job-service")
                .register(registry);
    }
}
