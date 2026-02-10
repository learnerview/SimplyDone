package com.learnerview.simplydone.service;

import com.learnerview.simplydone.dto.JobResponse;
import com.learnerview.simplydone.dto.QueueStatsResponse;
import com.learnerview.simplydone.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AdminService {

    QueueStatsResponse getStats();

    Page<JobResponse> listJobs(Pageable pageable);

    Page<JobResponse> listJobsByStatus(JobStatus status, Pageable pageable);

    List<JobResponse> getRecentJobs();

    List<JobResponse> getDlqJobs();

    void retryDlqJob(String jobId);

    void clearQueues();
}
