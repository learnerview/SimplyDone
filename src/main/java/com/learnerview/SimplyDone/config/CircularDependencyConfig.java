package com.learnerview.SimplyDone.config;

import com.learnerview.SimplyDone.service.JobService;
import com.learnerview.SimplyDone.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class to handle circular dependencies between services.
 * 
 * This class resolves the circular dependency between JobService and RetryService
 * by using setter injection after bean creation.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CircularDependencyConfig {

    private final JobService jobService;
    private final RetryService retryService;

    /**
     * Set up the circular dependencies after both beans are created.
     */
    @PostConstruct
    public void setupCircularDependencies() {
        log.info("Setting up circular dependencies between JobService and RetryService");
        
        // Set up the circular references
        jobService.setRetryService(retryService);
        retryService.setJobService(jobService);
        
        log.info("Circular dependencies setup completed");
    }
}
