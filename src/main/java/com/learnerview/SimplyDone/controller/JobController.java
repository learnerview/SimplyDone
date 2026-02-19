package com.learnerview.SimplyDone.controller;

import com.learnerview.SimplyDone.dto.ApiResponse;
import com.learnerview.SimplyDone.dto.JobMapper;
import com.learnerview.SimplyDone.dto.JobResponse;
import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobSubmissionResponse;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.exception.RateLimitException;
import com.learnerview.SimplyDone.exception.ResourceNotFoundException;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.service.JobService;
import com.learnerview.SimplyDone.service.RateLimitingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


// handles the main job endpoints - submit, get, cancel, rate limit check, health
// every request goes through rate limiting before hitting the service layer
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "simplydone.scheduler.api",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class JobController {
    
    private final JobService jobService;
    private final RateLimitingService rateLimitingService;
    
    // submit a new job - rate limiting enforced before service call
    @PostMapping
    public ResponseEntity<ApiResponse<JobSubmissionResponse>> submitJob(
            @Valid @RequestBody JobSubmissionRequest request) {

        log.info("Job submission request from user: {}", request.getUserId());

        if (!rateLimitingService.isAllowed(request.getUserId())) {
            RateLimitStatus status = rateLimitingService.getRateLimitStatus(request.getUserId());
            throw new RateLimitException(
                String.format("Rate limit exceeded for user: %s", request.getUserId()),
                status.resetTimeSeconds()
            );
        }

        JobSubmissionResponse response = jobService.submitJob(request);

        log.info("Job submitted successfully: {} type={} user={}", response.getId(), request.getJobType(), request.getUserId());

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(HttpStatus.CREATED.value(), response,
                "Job submitted successfully and queued for execution"));
    }
    
    // get a job by its id - throws 404 if not found
    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable String jobId) {
        log.info("Retrieving job details: {}", jobId);

        Job job = jobService.getJobById(jobId);
        if (job == null) {
            log.warn("Job not found: {}", jobId);
            throw new ResourceNotFoundException("Job", jobId);
        }

        JobResponse jobResponse = JobMapper.toJobResponse(job);

        log.info("Job retrieved: {} status={} type={}", jobId, job.getStatus(), job.getJobType());
        return ResponseEntity.ok(
            ApiResponse.success(jobResponse, "Job retrieved successfully")
        );
    }
    
    // cancel a job - returns 404 if the job doesn't exist or is already done
    @DeleteMapping("/{jobId}")
    public ResponseEntity<ApiResponse<String>> cancelJob(@PathVariable String jobId) {
        log.info("Cancellation request for job: {}", jobId);

        boolean cancelled = jobService.cancelJob(jobId);
        if (!cancelled) {
            log.warn("Job cancellation failed: {}", jobId);
            throw new ResourceNotFoundException("Job not found or could not be cancelled", jobId);
        }

        log.info("Job cancelled successfully: {}", jobId);

        return ResponseEntity.ok(
            ApiResponse.success("Job cancelled successfully")
        );
    }
    
    // check how many requests a user has made and when their limit resets
    @GetMapping("/rate-limit/{userId}")
    public ResponseEntity<ApiResponse<RateLimitStatus>> getRateLimitStatus(
            @PathVariable String userId) {
        log.debug("Rate limit status request for user: {}", userId);

        RateLimitStatus status = rateLimitingService.getRateLimitStatus(userId);

        log.info("Rate limit status retrieved for user: {} ({}/{})", userId, status.currentCount(), status.maxRequests());
        return ResponseEntity.ok(
            ApiResponse.success(status, "Rate limit status retrieved")
        );
    }
    
    // basic health check - returns queue counts so we know the system is up
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<?>> health() {
        long[] queueSizes = jobService.getQueueSizes();
        long high = (queueSizes != null && queueSizes.length > 0) ? queueSizes[0] : 0;
        long low = (queueSizes != null && queueSizes.length > 1) ? queueSizes[1] : 0;

        log.info("Health check - queued={} (high={}, low={})", high + low, high, low);
        return ResponseEntity.ok(
            ApiResponse.success("Job service is operational and ready to accept jobs")
        );
    }
}

