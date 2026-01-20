package com.learnerview.SimplyDone.controller;

import com.learnerview.SimplyDone.model.DeadLetterJob;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.repository.JobRepository;
import com.learnerview.SimplyDone.service.JobService;
import com.learnerview.SimplyDone.service.RateLimitingService;
import com.learnerview.SimplyDone.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin controller for system statistics and monitoring.
 * 
 * This controller provides endpoints for:
 * - System statistics (queue sizes, execution counts)
 * - Queue inspection (view jobs in queues)
 * - System health monitoring
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final JobRepository jobRepository;
    private final JobService jobService;
    private final RateLimitingService rateLimitingService;
    private final RetryService retryService;
    
    /**
     * Gets basic system statistics.
     * 
     * @return system statistics including queue sizes and execution counts
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.debug("Admin request for system statistics");
        
        // Get queue sizes
        long[] queueSizes = jobService.getQueueSizes();
        
        // Get execution statistics
        long executedJobs = jobRepository.getExecutedJobsCount();
        long rejectedJobs = jobRepository.getRejectedJobsCount();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("highQueueSize", queueSizes[0]);
        stats.put("lowQueueSize", queueSizes[1]);
        stats.put("totalQueueSize", queueSizes[0] + queueSizes[1]);
        stats.put("executedJobs", executedJobs);
        stats.put("rejectedJobs", rejectedJobs);
        stats.put("totalProcessed", executedJobs + rejectedJobs);
        stats.put("timestamp", java.time.Instant.now().toString());
        
        log.info("Stats requested - High: {}, Low: {}, Executed: {}, Rejected: {}", 
                queueSizes[0], queueSizes[1], executedJobs, rejectedJobs);
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Gets detailed information about jobs in the high priority queue.
     * 
     * @return list of jobs in high priority queue
     */
    @GetMapping("/queues/high")
    public ResponseEntity<List<Job>> getHighPriorityQueue() {
        log.debug("Admin request for high priority queue inspection");
        List<Job> jobs = jobRepository.getAllJobsInQueue(JobPriority.HIGH);
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * Gets detailed information about jobs in the low priority queue.
     * 
     * @return list of jobs in low priority queue
     */
    @GetMapping("/queues/low")
    public ResponseEntity<List<Job>> getLowPriorityQueue() {
        log.debug("Admin request for low priority queue inspection");
        List<Job> jobs = jobRepository.getAllJobsInQueue(JobPriority.LOW);
        return ResponseEntity.ok(jobs);
    }
    
    /**
     * Gets system health information.
     * 
     * @return health status and system information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new HashMap<>();
        
        // Basic health info
        health.put("status", "UP");
        health.put("service", "SimplyDone Job Scheduler");
        health.put("version", "1.0.0");
        health.put("timestamp", java.time.Instant.now().toString());
        
        // System info
        Runtime runtime = Runtime.getRuntime();
        health.put("jvm", Map.of(
            "maxMemory", runtime.maxMemory(),
            "totalMemory", runtime.totalMemory(),
            "freeMemory", runtime.freeMemory(),
            "usedMemory", runtime.totalMemory() - runtime.freeMemory()
        ));
        
        // Queue health
        long[] queueSizes = jobService.getQueueSizes();
        health.put("queues", Map.of(
            "highPrioritySize", queueSizes[0],
            "lowPrioritySize", queueSizes[1],
            "totalSize", queueSizes[0] + queueSizes[1]
        ));
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * Gets retry statistics.
     * 
     * @return retry statistics
     */
    @GetMapping("/retry-stats")
    public ResponseEntity<RetryService.RetryStatistics> getRetryStats() {
        log.debug("Admin request for retry statistics");
        RetryService.RetryStatistics stats = retryService.getRetryStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Gets rate limit status for a specific user.
     * 
     * @param userId the user ID
     * @return rate limit status
     */
    @GetMapping("/rate-limit/{userId}")
    public ResponseEntity<RateLimitingService.RateLimitStatus> getUserRateLimitStatus(@PathVariable String userId) {
        log.debug("Admin request for rate limit status of user: {}", userId);
        RateLimitingService.RateLimitStatus status = rateLimitingService.getRateLimitStatus(userId);
        return ResponseEntity.ok(status);
    }
    
    /**
     * Clears all jobs from queues (admin operation).
     * 
     * @return response indicating operation result
     */
    @DeleteMapping("/queues/clear")
    public ResponseEntity<Map<String, Object>> clearAllQueues() {
        log.warn("Admin request to clear all queues");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get current queue sizes before clearing
            long[] queueSizes = jobService.getQueueSizes();
            long highPrioritySize = queueSizes[0];
            long lowPrioritySize = queueSizes[1];
            long totalSize = highPrioritySize + lowPrioritySize;
            
            // Use the efficient bulk delete from repository
            int clearedCount = jobRepository.clearAllQueues();
            
            response.put("success", true);
            response.put("message", "Queue clear operation completed");
            response.put("clearedJobs", Map.of(
                "highPriority", highPrioritySize,
                "lowPriority", lowPrioritySize,
                "total", clearedCount
            ));
            
            log.info("All queues cleared. {} jobs removed", clearedCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to clear queues: {}", e.getMessage());
            
            response.put("success", false);
            response.put("error", "Failed to clear queues");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Clears a specific priority queue.
     * 
     * @param priority the priority queue to clear
     * @return response indicating operation result
     */
    @DeleteMapping("/queues/clear/{priority}")
    public ResponseEntity<Map<String, Object>> clearQueue(@PathVariable String priority) {
        log.warn("Admin request to clear {} priority queue", priority);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            JobPriority jobPriority;
            try {
                jobPriority = JobPriority.valueOf(priority.toUpperCase());
            } catch (IllegalArgumentException e) {
                response.put("success", false);
                response.put("error", "Invalid priority");
                response.put("message", "Priority must be HIGH or LOW");
                return ResponseEntity.badRequest().body(response);
            }
            
            int clearedCount = jobRepository.clearQueue(jobPriority);
            
            response.put("success", true);
            response.put("message", priority.toUpperCase() + " priority queue cleared");
            response.put("clearedJobs", clearedCount);
            
            log.info("{} priority queue cleared. {} jobs removed", priority.toUpperCase(), clearedCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to clear {} priority queue: {}", priority, e.getMessage());
            
            response.put("success", false);
            response.put("error", "Failed to clear queue");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Gets all jobs in the dead letter queue.
     * 
     * @return list of dead letter jobs
     */
    @GetMapping("/dead-letter-queue")
    public ResponseEntity<Map<String, Object>> getDeadLetterQueue() {
        log.debug("Admin request for dead letter queue");
        
        try {
            List<DeadLetterJob> deadLetterJobs = retryService.getDeadLetterJobs();
            
            Map<String, Object> response = new HashMap<>();
            response.put("deadLetterJobs", deadLetterJobs);
            response.put("totalJobs", deadLetterJobs.size());
            response.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to retrieve dead letter queue: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve dead letter queue");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Clears the dead letter queue.
     * 
     * @return response indicating operation result
     */
    @DeleteMapping("/dead-letter-queue")
    public ResponseEntity<Map<String, Object>> clearDeadLetterQueue() {
        log.warn("Admin request to clear dead letter queue");
        
        try {
            int clearedCount = retryService.clearDeadLetterQueue();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Dead letter queue cleared");
            response.put("clearedJobs", clearedCount);
            
            log.info("Dead letter queue cleared. {} jobs removed", clearedCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to clear dead letter queue: {}", e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to clear dead letter queue");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Retries a job from the dead letter queue.
     * 
     * @param jobId the ID of the job to retry
     * @return response indicating operation result
     */
    @PostMapping("/dead-letter-queue/{jobId}/retry")
    public ResponseEntity<Map<String, Object>> retryDeadLetterJob(@PathVariable String jobId) {
        log.info("Admin request to retry dead letter job: {}", jobId);
        
        try {
            boolean success = retryService.retryDeadLetterJob(jobId);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("success", true);
                response.put("message", "Job successfully retried from dead letter queue");
                response.put("jobId", jobId);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Job not found in dead letter queue");
                response.put("jobId", jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
        } catch (Exception e) {
            log.error("Failed to retry dead letter job {}: {}", jobId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retry dead letter job");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("jobId", jobId);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Gets system performance metrics.
     * 
     * @return performance metrics
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceMetrics() {
        log.debug("Admin request for performance metrics");
        
        Map<String, Object> metrics = new HashMap<>();
        
        // JVM metrics
        Runtime runtime = Runtime.getRuntime();
        metrics.put("jvm", Map.of(
            "maxMemory", runtime.maxMemory(),
            "totalMemory", runtime.totalMemory(),
            "freeMemory", runtime.freeMemory(),
            "usedMemory", runtime.totalMemory() - runtime.freeMemory(),
            "availableProcessors", runtime.availableProcessors()
        ));
        
        // Job processing metrics
        long[] queueSizes = jobService.getQueueSizes();
        long executedJobs = jobRepository.getExecutedJobsCount();
        long rejectedJobs = jobRepository.getRejectedJobsCount();
        
        metrics.put("jobProcessing", Map.of(
            "highPriorityQueueSize", queueSizes[0],
            "lowPriorityQueueSize", queueSizes[1],
            "totalQueueSize", queueSizes[0] + queueSizes[1],
            "executedJobs", executedJobs,
            "rejectedJobs", rejectedJobs,
            "totalProcessed", executedJobs + rejectedJobs,
            "successRate", executedJobs + rejectedJobs > 0 ? 
                (double) executedJobs / (executedJobs + rejectedJobs) * 100 : 0.0
        ));
        
        // Retry metrics
        RetryService.RetryStatistics retryStats = retryService.getRetryStatistics();
        metrics.put("retry", Map.of(
            "retryingJobs", retryStats.retryingJobs(),
            "totalRetryAttempts", retryStats.totalAttempts(),
            "maxRetryAttempts", retryStats.maxAttempts()
        ));
        
        metrics.put("timestamp", java.time.Instant.now().toString());
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Gets jobs by user ID.
     * 
     * @param userId the user ID
     * @return list of jobs for the user
     */
    @GetMapping("/jobs/user/{userId}")
    public ResponseEntity<Map<String, Object>> getJobsByUser(@PathVariable String userId) {
        log.debug("Admin request for jobs of user: {}", userId);
        
        try {
            List<Job> highPriorityJobs = jobRepository.getAllJobsInQueue(JobPriority.HIGH);
            List<Job> lowPriorityJobs = jobRepository.getAllJobsInQueue(JobPriority.LOW);
            
            // Filter jobs by user ID
            List<Job> userHighJobs = highPriorityJobs.stream()
                .filter(job -> job.getUserId().equals(userId))
                .toList();
            
            List<Job> userLowJobs = lowPriorityJobs.stream()
                .filter(job -> job.getUserId().equals(userId))
                .toList();
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("highPriorityJobs", userHighJobs);
            response.put("lowPriorityJobs", userLowJobs);
            response.put("totalJobs", userHighJobs.size() + userLowJobs.size());
            response.put("timestamp", java.time.Instant.now().toString());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to retrieve jobs for user {}: {}", userId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to retrieve jobs");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("userId", userId);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
