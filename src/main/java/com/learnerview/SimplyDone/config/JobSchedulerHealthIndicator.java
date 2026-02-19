package com.learnerview.SimplyDone.config;

import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.repository.JobRepository;
import com.learnerview.SimplyDone.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom health indicator for the job scheduler.
 * 
 * This health indicator checks:
 * - Queue sizes and health
 * - Job processing statistics
 * - Overall scheduler status
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobSchedulerHealthIndicator implements HealthIndicator {

    private final JobRepository jobRepository;
    private final JobService jobService;

    @Override
    public Health health() {
        try {
            Map<String, Object> details = new HashMap<>();
            
            // Get queue sizes
            long[] queueSizes = jobService.getQueueSizes();
            long highPrioritySize = queueSizes[0];
            long lowPrioritySize = queueSizes[1];
            long totalSize = highPrioritySize + lowPrioritySize;
            
            // Get execution statistics
            long executedJobs = jobRepository.getExecutedJobsCount();
            long rejectedJobs = jobRepository.getRejectedJobsCount();
            
            details.put("queues", Map.of(
                "high_priority_size", highPrioritySize,
                "low_priority_size", lowPrioritySize,
                "total_size", totalSize
            ));
            
            details.put("statistics", Map.of(
                "executed_jobs", executedJobs,
                "rejected_jobs", rejectedJobs,
                "total_processed", executedJobs + rejectedJobs
            ));
            
            // Determine health status
            Health.Builder healthBuilder;
            
            // Consider unhealthy if queues are too large (>1000 jobs)
            if (totalSize > 1000) {
                healthBuilder = Health.outOfService();
                details.put("status", "Queues overloaded");
            } else if (totalSize > 500) {
                healthBuilder = Health.unknown();
                details.put("status", "Queues getting full");
            } else {
                healthBuilder = Health.up();
                details.put("status", "Healthy");
            }
            
            return healthBuilder
                    .withDetails(details)
                    .build();
                    
        } catch (Exception e) {
            log.error("Health check failed for job scheduler", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("service", "Job Scheduler")
                    .build();
        }
    }
}
