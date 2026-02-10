package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.dto.*;
import com.learnerview.simplydone.handler.JobHandlerRegistry;
import com.learnerview.simplydone.service.AdminService;
import com.learnerview.simplydone.service.JobSubmissionService;
import com.learnerview.simplydone.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobSubmissionService submissionService;
    private final WorkflowService workflowService;
    private final AdminService adminService;
    private final JobHandlerRegistry registry;

    @PostMapping
    public ResponseEntity<ApiResponse<JobSubmissionResponse>> submitJob(
            @Valid @RequestBody JobSubmissionRequest request) {
        JobSubmissionResponse resp = submissionService.submit(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<JobSubmissionResponse>builder()
                        .success(true).message("Job queued").data(resp).build());
    }

    @PostMapping("/workflow")
    public ResponseEntity<ApiResponse<List<JobSubmissionResponse>>> submitWorkflow(
            @Valid @RequestBody WorkflowRequest request) {
        List<JobSubmissionResponse> resp = workflowService.submitWorkflow(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.<List<JobSubmissionResponse>>builder()
                        .success(true).message("Workflow queued").data(resp).build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(@PathVariable String id) {
        JobResponse resp = submissionService.getJob(id);
        return ResponseEntity.ok(ApiResponse.<JobResponse>builder()
                .success(true).data(resp).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> cancelJob(@PathVariable String id) {
        submissionService.cancelJob(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Job cancelled").build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<JobResponse>>> listJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<JobResponse> jobs = adminService.listJobs(PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.<Page<JobResponse>>builder()
                .success(true).data(jobs).build());
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<HandlerInfoResponse>>> getTypes() {
        return ResponseEntity.ok(ApiResponse.<List<HandlerInfoResponse>>builder()
                .success(true).data(registry.getHandlerInfo()).build());
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        QueueStatsResponse stats = adminService.getStats();
        Map<String, Object> health = Map.of(
                "status", "UP",
                "handlers", registry.getRegisteredTypes().size(),
                "queued", stats.getTotalQueued(),
                "running", stats.getTotalRunning()
        );
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true).data(health).build());
    }
}
