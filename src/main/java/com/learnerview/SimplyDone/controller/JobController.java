package com.learnerview.SimplyDone.controller;

import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobSubmissionResponse;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.repository.JobRepository;
import com.learnerview.SimplyDone.service.JobService;
import com.learnerview.SimplyDone.service.RateLimitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for job submission and management.
 * 
 * This controller provides endpoints for:
 * - Submitting new jobs with rate limiting
 * - Getting job submission status
 * - Rate limit information
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {
    
    private final JobService jobService;
    private final RateLimitingService rateLimitingService;
    private final JobRepository jobRepository;
    
    /**
     * Submits a new job to the scheduler.
     * 
     * @param request the job submission request
     * @return response with job details or error if rate limited
     */
    @PostMapping
    public ResponseEntity<?> submitJob(@Valid @RequestBody JobSubmissionRequest request) {
        log.info("Received job submission request from user: {}", request.getUserId());
        
        // Check rate limit
        if (!rateLimitingService.isAllowed(request.getUserId())) {
            jobRepository.incrementRejectedJobsCounter();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Rate limit exceeded");
            errorResponse.put("message", "Too many requests. Maximum 10 jobs per minute allowed.");
            
            RateLimitingService.RateLimitStatus status = rateLimitingService.getRateLimitStatus(request.getUserId());
            errorResponse.put("retryAfter", status.resetTimeSeconds());
            errorResponse.put("limit", status.maxRequests());
            
            log.warn("Rate limit exceeded for user: {}", request.getUserId());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
        }
        
        try {
            JobSubmissionResponse response = jobService.submitJob(request);
            log.info("Job submitted successfully: {}", response.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            log.error("Failed to submit job: {}", e.getMessage());
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Job submission failed");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Gets the current rate limit status for a user.
     * 
     * @param userId the user identifier
     * @return rate limit status information
     */
    @GetMapping("/rate-limit/{userId}")
    public ResponseEntity<RateLimitingService.RateLimitStatus> getRateLimitStatus(@PathVariable String userId) {
        RateLimitingService.RateLimitStatus status = rateLimitingService.getRateLimitStatus(userId);
        return ResponseEntity.ok(status);
    }
    
    /**
     * Cancels a job by ID.
     * 
     * @param jobId the ID of the job to cancel
     * @return response indicating success or failure
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable String jobId) {
        log.info("Received cancellation request for job: {}", jobId);
        
        try {
            boolean cancelled = jobService.cancelJob(jobId);
            
            Map<String, Object> response = new HashMap<>();
            if (cancelled) {
                response.put("success", true);
                response.put("message", "Job cancelled successfully");
                response.put("jobId", jobId);
                log.info("Job {} cancelled successfully", jobId);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Job not found or could not be cancelled");
                response.put("jobId", jobId);
                log.warn("Job {} not found or could not be cancelled", jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
        } catch (Exception e) {
            log.error("Failed to cancel job {}: {}", jobId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Job cancellation failed");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("jobId", jobId);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Gets job details by ID.
     * 
     * @param jobId the ID of the job to retrieve
     * @return job details or error if not found
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> getJob(@PathVariable String jobId) {
        log.info("Received request for job details: {}", jobId);
        
        try {
            Job job = jobService.getJobById(jobId);
            
            if (job != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("job", job);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Job not found");
                errorResponse.put("jobId", jobId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
            }
            
        } catch (Exception e) {
            log.error("Failed to retrieve job {}: {}", jobId, e.getMessage());
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Failed to retrieve job");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("jobId", jobId);
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Health check endpoint for the job service.
     * 
     * @return health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "Job Scheduler");
        status.put("timestamp", java.time.Instant.now().toString());
        return ResponseEntity.ok(status);
    }
}
