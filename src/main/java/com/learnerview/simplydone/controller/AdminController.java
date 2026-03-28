package com.learnerview.simplydone.controller;

import com.learnerview.simplydone.dto.*;
import com.learnerview.simplydone.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Profile("api")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

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

    /* ── API Key Management ────────────────────────────────── */

    @GetMapping("/keys")
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> listKeys() {
        return ResponseEntity.ok(ApiResponse.<List<ApiKeyResponse>>builder()
                .success(true).data(adminService.listKeys()).build());
    }

    @PostMapping("/keys")
    public ResponseEntity<ApiResponse<ApiKeyResponse >> createKey(@RequestBody ApiKeyRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.<ApiKeyResponse>builder()
                .success(true).message("API Key issued").data(adminService.createKey(request)).build());
    }

    @DeleteMapping("/keys/{id}")
    public ResponseEntity<ApiResponse<Void>> revokeKey(@PathVariable String id) {
        adminService.revokeKey(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("API Key revoked").build());
    }
}
