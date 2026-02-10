package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.dto.*;
import com.learnerview.simplydone.handler.JobHandlerRegistry;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final JobHandlerRegistry registry;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<QueueStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.<QueueStatsResponse>builder()
                .success(true).data(adminService.getStats()).build());
    }

    @GetMapping("/queues")
    public ResponseEntity<ApiResponse<QueueStatsResponse>> queues() {
        return ResponseEntity.ok(ApiResponse.<QueueStatsResponse>builder()
                .success(true).data(adminService.getStats()).build());
    }

    @DeleteMapping("/queues")
    public ResponseEntity<ApiResponse<Void>> clearQueues() {
        adminService.clearQueues();
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("All queues cleared").build());
    }

    @GetMapping("/dlq")
    public ResponseEntity<ApiResponse<List<JobResponse>>> dlq() {
        return ResponseEntity.ok(ApiResponse.<List<JobResponse>>builder()
                .success(true).data(adminService.getDlqJobs()).build());
    }

    @PostMapping("/dlq/{id}/retry")
    public ResponseEntity<ApiResponse<Void>> retryDlq(@PathVariable String id) {
        adminService.retryDlqJob(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Job re-queued from DLQ").build());
    }

    @GetMapping("/handlers")
    public ResponseEntity<ApiResponse<List<HandlerInfoResponse>>> handlers() {
        return ResponseEntity.ok(ApiResponse.<List<HandlerInfoResponse>>builder()
                .success(true).data(registry.getHandlerInfo()).build());
    }
}
