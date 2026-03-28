package com.learnerview.simplydone.service;

import com.learnerview.simplydone.dto.JobResponse;
import com.learnerview.simplydone.dto.QueueStatsResponse;
import com.learnerview.simplydone.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminService {

    QueueStatsResponse getStats();

    QueueStatsResponse getStats(String producer);

    Page<JobResponse> listJobs(Pageable pageable);

    Page<JobResponse> listJobsByStatus(JobStatus status, Pageable pageable);

    List<JobResponse> getRecentJobs();

    List<JobResponse> getDlqJobs();

    void retryDlqJob(String jobId);

    void clearQueues();

    /* ── API Key Management ────────────────────────────────── */

    List<com.learnerview.simplydone.dto.ApiKeyResponse> listKeys();

    com.learnerview.simplydone.dto.ApiKeyResponse createKey(com.learnerview.simplydone.dto.ApiKeyRequest request);

    void revokeKey(String keyId);
}
