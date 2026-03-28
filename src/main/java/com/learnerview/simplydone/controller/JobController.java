package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.dto.*;
import com.learnerview.simplydone.service.AdminService;
import com.learnerview.simplydone.service.JobSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@Profile("api")
@RequiredArgsConstructor
public class JobController {

    private final JobSubmissionService submissionService;
    private final AdminService adminService;

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobSubmissionResponse>> submitJob(
            @AuthenticationPrincipal String producer,
            @Valid @RequestBody JobSubmissionRequest request) {
        JobSubmissionResponse resp = submissionService.submit(producer, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<JobSubmissionResponse>builder()
                        .success(true).message("Job queued").data(resp).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(
            Authentication auth,
            @AuthenticationPrincipal String producer,
            @PathVariable String id) {
        JobResponse resp = isAdmin(auth)
                ? submissionService.getJob(id)
                : submissionService.getJob(producer, id);
        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true).data(resp).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelJob(@AuthenticationPrincipal String producer, @PathVariable String id) {
        submissionService.cancelJob(producer, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Job cancelled").build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobResponse>>> listJobs(
            Authentication auth,
            @AuthenticationPrincipal String producer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<JobResponse> jobs = isAdmin(auth)
                ? submissionService.listJobs(PageRequest.of(page, size))
                : submissionService.listJobs(producer, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.<Page<JobResponse>>builder()
                .success(true).data(jobs).build());
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<HandlerInfoResponse>>> getTypes() {
        HandlerInfoResponse external = HandlerInfoResponse.builder()
                .jobType("external")
                .description("Generic external HTTP execution")
                .handlerClass("ExternalHttpExecutor")
                .build();
        return ResponseEntity.ok(ApiResponse.<List<HandlerInfoResponse>>builder()
                .success(true).data(List.of(external)).build());
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health(
            Authentication auth,
            @AuthenticationPrincipal String producer) {
        QueueStatsResponse stats = isAdmin(auth)
                ? adminService.getStats()
                : adminService.getStats(producer);
        Map<String, Object> health = Map.ofEntries(
                Map.entry("status", "UP"),
                Map.entry("handlers", 1),
                Map.entry("highQueueSize", stats.getHighQueueSize()),
                Map.entry("normalQueueSize", stats.getNormalQueueSize()),
                Map.entry("lowQueueSize", stats.getLowQueueSize()),
                Map.entry("totalQueued", stats.getTotalQueued()),
                Map.entry("totalRunning", stats.getTotalRunning()),
                Map.entry("totalSuccess", stats.getTotalSuccess()),
                Map.entry("totalFailed", stats.getTotalFailed()),
                Map.entry("totalDlq", stats.getTotalDlq()),
                Map.entry("totalProcessed", stats.getTotalProcessed()),
                Map.entry("successRate", stats.getSuccessRate()),
                Map.entry("retryRate", stats.getRetryRate()),
                Map.entry("throughputPerMinute", stats.getThroughputPerMinute()),
                Map.entry("avgLatencyMs", stats.getAvgLatencyMs())
        );
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true).data(health).build());
    }
}
