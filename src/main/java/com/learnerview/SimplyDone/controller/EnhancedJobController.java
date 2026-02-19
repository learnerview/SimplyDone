package com.learnerview.SimplyDone.controller;

import com.learnerview.SimplyDone.dto.ApiResponse;
import com.learnerview.SimplyDone.dto.EnhancedJobSubmissionRequest;
import com.learnerview.SimplyDone.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// handles the advanced job submission stuff
// like the basic JobController but with extra options:
//   - schedule a job to run at a specific future time (epochSeconds)
//   - set custom timeouts and max retry counts per job
//   - declare job dependencies (one job runs after another)
//   - batch status check and batch cancel
//
// endpoints:
//   POST   /api/jobs/enhanced            - submit with extra options
//   GET    /api/jobs/enhanced/batch/{id} - check how a batch is going
//   DELETE /api/jobs/enhanced/batch/{id} - cancel all jobs in a batch
//
// can be turned off in config: simplydone.scheduler.api.enabled=false
@RestController
@RequestMapping("/api/jobs/enhanced")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "simplydone.scheduler.api",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class EnhancedJobController {

    private final JobService jobService;

    // submits a job with the advanced options - validation enforced in service layer
    @PostMapping
    public ResponseEntity<ApiResponse<?>> submitEnhancedJob(
            @Valid @RequestBody EnhancedJobSubmissionRequest request) {

        log.info("Enhanced job submission from user: {}", request.getUserId());

        var response = jobService.submitEnhancedJob(request);
        log.info("Enhanced job submitted: {} user={} schedule={}", response.getId(), request.getUserId(), request.getScheduledAtEpochSeconds());

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(HttpStatus.CREATED.value(), response, "Enhanced job submitted successfully"));
    }

    // returns the current status of a batch - how many done, pending, failed etc.
    @GetMapping("/batch/{batchId}")
    public ResponseEntity<ApiResponse<?>> getBatchStatus(@PathVariable String batchId) {
        var batchStatus = jobService.getBatchStatus(batchId);
        log.debug("Batch status retrieved: {}", batchId);
        return ResponseEntity.ok(ApiResponse.success(batchStatus, "Batch status retrieved"));
    }

    // cancels all the jobs in a batch
    @DeleteMapping("/batch/{batchId}")
    public ResponseEntity<ApiResponse<String>> cancelBatch(@PathVariable String batchId) {
        jobService.cancelBatch(batchId);
        log.warn("Batch cancelled: {}", batchId);
        return ResponseEntity.ok(ApiResponse.success("Batch cancelled successfully"));
    }
}
