package com.learnerview.SimplyDone.controller;

import com.learnerview.SimplyDone.dto.ApiResponse;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.exception.ResourceNotFoundException;
import com.learnerview.SimplyDone.model.DeadLetterJob;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.service.AdminService;
import com.learnerview.SimplyDone.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// admin endpoints for system monitoring and management
// all operations go through AdminService only (Rule 1: strict layer boundaries)
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "simplydone.scheduler.api",
    name = "admin-endpoints",
    havingValue = "true",
    matchIfMissing = true
)
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<?>> getStats() {
        Map<String, Object> stats = adminService.getSystemStats();
        log.info("Stats retrieved: queued={} executed={} rejected={}", stats.get("totalQueueSize"), stats.get("executedJobs"), stats.get("rejectedJobs"));
        return ResponseEntity.ok(ApiResponse.success(stats, "System statistics retrieved"));
    }

    @GetMapping("/queues/high")
    public ResponseEntity<ApiResponse<List<?>>> getHighPriorityQueue() {
        List<?> jobs = adminService.getQueueJobs(JobPriority.HIGH);
        log.debug("High priority queue retrieved - {} jobs", jobs.size());
        return ResponseEntity.ok(ApiResponse.success(jobs, "High priority queue retrieved"));
    }

    @GetMapping("/queues/low")
    public ResponseEntity<ApiResponse<List<?>>> getLowPriorityQueue() {
        List<?> jobs = adminService.getQueueJobs(JobPriority.LOW);
        log.debug("Low priority queue retrieved - {} jobs", jobs.size());
        return ResponseEntity.ok(ApiResponse.success(jobs, "Low priority queue retrieved"));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<?>> getHealth() {
        Map<String, Object> health = adminService.getHealthInfo();
        log.debug("Health check completed: status={}", health.get("status"));
        return ResponseEntity.ok(ApiResponse.success(health, "System health check completed"));
    }

    @GetMapping("/retry-stats")
    public ResponseEntity<ApiResponse<RetryService.RetryStatistics>> getRetryStats() {
        RetryService.RetryStatistics stats = adminService.getRetryStatistics();
        log.debug("Retry statistics retrieved");
        return ResponseEntity.ok(ApiResponse.success(stats, "Retry statistics retrieved"));
    }

    @GetMapping("/rate-limit/{userId}")
    public ResponseEntity<ApiResponse<RateLimitStatus>> getUserRateLimitStatus(@PathVariable String userId) {
        RateLimitStatus status = adminService.getUserRateLimitStatus(userId);
        log.debug("Rate limit status retrieved for user: {}", userId);
        return ResponseEntity.ok(ApiResponse.success(status, "Rate limit status retrieved"));
    }

    @DeleteMapping("/queues/clear")
    public ResponseEntity<ApiResponse<?>> clearAllQueues() {
        Map<String, Object> result = adminService.clearQueues();
        log.warn("All queues cleared");
        return ResponseEntity.ok(ApiResponse.success(result, "All queues cleared successfully"));
    }

    @DeleteMapping("/queues/clear/{priority}")
    public ResponseEntity<ApiResponse<?>> clearQueue(@PathVariable String priority) {
        JobPriority jobPriority = JobPriority.valueOf(priority.toUpperCase());
        Map<String, Object> result = adminService.clearQueue(jobPriority);
        log.warn("Queue cleared - priority: {}", priority);
        return ResponseEntity.ok(ApiResponse.success(result, priority + " queue cleared successfully"));
    }

    @GetMapping("/dead-letter-queue")
    public ResponseEntity<ApiResponse<?>> getDeadLetterQueue() {
        List<DeadLetterJob> jobs = adminService.getDeadLetterJobs();
        log.debug("Dead letter queue retrieved - {} jobs", jobs.size());
        return ResponseEntity.ok(ApiResponse.success(jobs, "Dead letter queue retrieved"));
    }

    @DeleteMapping("/dead-letter-queue")
    public ResponseEntity<ApiResponse<String>> clearDeadLetterQueue() {
        int cleared = adminService.clearDeadLetterQueue();
        log.warn("Dead letter queue cleared - {} jobs", cleared);
        return ResponseEntity.ok(ApiResponse.success("Dead letter queue cleared - " + cleared + " jobs removed"));
    }

    @PostMapping("/dead-letter-queue/{jobId}/retry")
    public ResponseEntity<ApiResponse<String>> retryDeadLetterJob(@PathVariable String jobId) {
        boolean success = adminService.retryDeadLetterJob(jobId);
        if (!success) {
            log.warn("Job not found in dead letter queue: {}", jobId);
            throw new ResourceNotFoundException("Job not found in dead letter queue", jobId);
        }
        log.info("Job retried from dead letter queue: {}", jobId);
        return ResponseEntity.ok(ApiResponse.success("Job retried and moved back to queue"));
    }

    @GetMapping("/performance")
    public ResponseEntity<ApiResponse<?>> getPerformanceMetrics() {
        Map<String, Object> metrics = adminService.getPerformanceMetrics();
        log.debug("Performance metrics retrieved");
        return ResponseEntity.ok(ApiResponse.success(metrics, "Performance metrics retrieved"));
    }

    @GetMapping("/jobs/user/{userId}")
    public ResponseEntity<ApiResponse<?>> getJobsByUser(@PathVariable String userId) {
        Map<String, Object> userJobs = adminService.getJobsByUser(userId);
        log.debug("Jobs retrieved for user: {} - {} total", userId, userJobs.get("totalJobs"));
        return ResponseEntity.ok(ApiResponse.success(userJobs, "User jobs retrieved"));
    }
}
