package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.dto.JobResponse;
import com.learnerview.simplydone.dto.QueueStatsResponse;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.exception.JobNotFoundException;
import com.learnerview.simplydone.mapper.JobMapper;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import com.learnerview.simplydone.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final JobEntityRepository jobRepo;
    private final QueueRepository queueRepo;
    private final JobMapper jobMapper;

    @Override
    public QueueStatsResponse getStats() {
        long success = jobRepo.countByStatus(JobStatus.SUCCESS);
        long failed  = jobRepo.countByStatus(JobStatus.FAILED);
        long dlq     = jobRepo.countByStatus(JobStatus.DLQ);
        long processed = success + failed + dlq;

        // Success rate (%)
        double successRate = processed > 0 ? (success * 100.0 / processed) : 0.0;

        // Retry rate: % of processed jobs that needed ≥1 retry
        long retried = jobRepo.countByAttemptCountGreaterThanAndStatusIn(
                0, List.of(JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.DLQ));
        double retryRate = processed > 0 ? (retried * 100.0 / processed) : 0.0;

        // Throughput: successful jobs completed in the last 60 seconds
        Instant oneMinAgo = Instant.now().minusSeconds(60);
        long recentSuccess = jobRepo.countByStatusAndCompletedAtAfter(JobStatus.SUCCESS, oneMinAgo);
        double throughput = recentSuccess; // jobs/min

        // Average latency: compute from recent successful jobs with timing data
        List<JobEntity> recentJobs = jobRepo.findCompletedWithTimingsSince(
                JobStatus.SUCCESS, Instant.now().minusSeconds(300)); // last 5 min
        double avgLatency = recentJobs.stream()
                .filter(j -> j.getStartedAt() != null && j.getCompletedAt() != null)
                .mapToLong(j -> j.getCompletedAt().toEpochMilli() - j.getStartedAt().toEpochMilli())
                .average()
                .orElse(0.0);

        return QueueStatsResponse.builder()
                .highQueueSize(queueRepo.queueSize(JobPriority.HIGH))
                .normalQueueSize(queueRepo.queueSize(JobPriority.NORMAL))
                .lowQueueSize(queueRepo.queueSize(JobPriority.LOW))
                .totalQueued(jobRepo.countByStatus(JobStatus.QUEUED))
                .totalRunning(jobRepo.countByStatus(JobStatus.RUNNING))
                .totalSuccess(success)
                .totalFailed(failed)
                .totalDlq(dlq)
                .totalProcessed(processed)
                .successRate(Math.round(successRate * 10.0) / 10.0)
                .retryRate(Math.round(retryRate * 10.0) / 10.0)
                .throughputPerMinute(throughput)
                .avgLatencyMs(Math.round(avgLatency * 10.0) / 10.0)
                .build();
    }

    @Override
    public Page<JobResponse> listJobs(Pageable pageable) {
        return jobRepo.findAllByOrderByCreatedAtDesc(pageable)
                .map(jobMapper::toResponse);
    }

    @Override
    public Page<JobResponse> listJobsByStatus(JobStatus status, Pageable pageable) {
        return jobRepo.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(jobMapper::toResponse);
    }

    @Override
    public List<JobResponse> getRecentJobs() {
        return jobRepo.findTop20ByOrderByCreatedAtDesc().stream()
                .map(jobMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<JobResponse> getDlqJobs() {
        return jobRepo.findByStatus(JobStatus.DLQ).stream()
                .map(jobMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void retryDlqJob(String jobId) {
        JobEntity job = jobRepo.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
        if (job.getStatus() != JobStatus.DLQ) {
            throw new IllegalArgumentException("Job is not in DLQ: " + job.getStatus());
        }
        job.setStatus(JobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setScheduledAt(Instant.now());
        job.setCompletedAt(null);
        job.setResult(null);
        jobRepo.save(job);
        queueRepo.enqueue(jobId, job.getPriority(), Instant.now().toEpochMilli());
    }

    @Override
    public void clearQueues() {
        queueRepo.clearAll();
    }
}
