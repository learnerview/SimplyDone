package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.dto.ApiKeyRequest;
import com.learnerview.simplydone.dto.ApiKeyResponse;
import com.learnerview.simplydone.dto.JobResponse;
import com.learnerview.simplydone.dto.QueueStatsResponse;
import com.learnerview.simplydone.entity.ApiKeyEntity;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.exception.JobNotFoundException;
import com.learnerview.simplydone.mapper.JobMapper;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.ApiKeyRepository;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import com.learnerview.simplydone.service.AdminService;
import com.learnerview.simplydone.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminServiceImpl implements AdminService {

    private final JobEntityRepository jobRepo;
    private final QueueRepository queueRepo;
    private final JobMapper jobMapper;
    private final SseEmitterService sseEmitterService;
    private final ApiKeyRepository apiKeyRepo;

    /* ── Stats ─────────────────────────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public QueueStatsResponse getStats() {
        long success   = jobRepo.countByStatus(JobStatus.SUCCESS);
        long failed    = jobRepo.countByStatus(JobStatus.FAILED);
        long dlq       = jobRepo.countByStatus(JobStatus.DLQ);
        long processed = success + failed + dlq;

        double successRate = processed > 0 ? (success * 100.0 / processed) : 0.0;
        long retried = jobRepo.countByAttemptCountGreaterThanAndStatusIn(
                0, List.of(JobStatus.SUCCESS, JobStatus.FAILED, JobStatus.DLQ));
        double retryRate = processed > 0 ? (retried * 100.0 / processed) : 0.0;

        Instant oneMinAgo = Instant.now().minusSeconds(60);
        double throughput = jobRepo.countByStatusAndCompletedAtAfter(JobStatus.SUCCESS, oneMinAgo);

        List<JobEntity> recent = jobRepo.findCompletedWithTimingsSince(
                JobStatus.SUCCESS, Instant.now().minusSeconds(300));
        double avgLatency = recent.stream()
                .filter(j -> j.getStartedAt() != null && j.getCompletedAt() != null)
                .mapToLong(j -> j.getCompletedAt().toEpochMilli() - j.getStartedAt().toEpochMilli())
                .average().orElse(0.0);

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
    @Transactional(readOnly = true)
    public QueueStatsResponse getStats(String producer) {
        return QueueStatsResponse.builder()
                .totalQueued(jobRepo.countByProducerAndStatus(producer, JobStatus.QUEUED))
                .totalRunning(jobRepo.countByProducerAndStatus(producer, JobStatus.RUNNING))
                .totalSuccess(jobRepo.countByProducerAndStatus(producer, JobStatus.SUCCESS))
                .totalFailed(jobRepo.countByProducerAndStatus(producer, JobStatus.FAILED))
                .totalDlq(jobRepo.countByProducerAndStatus(producer, JobStatus.DLQ))
                .build();
    }

    /* ── Jobs ──────────────────────────────────────────── */

    @Override
    @Transactional(readOnly = true)
    public Page<JobResponse> listJobs(Pageable pageable) {
        return jobRepo.findAllByOrderByCreatedAtDesc(pageable).map(jobMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<JobResponse> listJobsByStatus(JobStatus status, Pageable pageable) {
        return jobRepo.findByStatusOrderByCreatedAtDesc(status, pageable).map(jobMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobResponse> getRecentJobs() {
        return jobRepo.findTop20ByOrderByCreatedAtDesc().stream()
                .map(jobMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobResponse> getDlqJobs() {
        return jobRepo.findByStatus(JobStatus.DLQ).stream()
                .map(jobMapper::toResponse).collect(Collectors.toList());
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
        job.setNextRunAt(Instant.now());
        job.setCompletedAt(null);
        job.setResult(null);
        jobRepo.save(job);
        queueRepo.enqueue(jobId, job.getPriority(), Instant.now().toEpochMilli());
        sseEmitterService.broadcast(job.getProducer(), "JOB_UPDATE",
                Map.of("id", jobId, "status", "QUEUED", "result", "Retried from DLQ"));
    }

    @Override
    public void clearQueues() {
        queueRepo.clearAll();
    }

    /* ── API Key Management (simple INSERT, multiple keys per producer allowed) ── */

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listKeys() {
        return apiKeyRepo.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ApiKeyResponse createKey(ApiKeyRequest request) {
        String secret = "sd_sk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        ApiKeyEntity entity = ApiKeyEntity.builder()
                .id(UUID.randomUUID().toString())
                .apiKey(secret)
                .producer(request.getProducer())
                .label(request.getLabel())
                .admin(request.isAdmin())
                .active(true)
                .createdAt(Instant.now())
                .build();
        return toResponse(apiKeyRepo.save(entity));
    }

    @Override
    public void revokeKey(String keyId) {
        apiKeyRepo.findById(keyId).ifPresent(key -> {
            key.setActive(false);
            apiKeyRepo.save(key);
        });
    }

    private ApiKeyResponse toResponse(ApiKeyEntity e) {
        return ApiKeyResponse.builder()
                .id(e.getId())
                .apiKey(e.getApiKey())
                .producer(e.getProducer())
                .label(e.getLabel())
                .active(e.isActive())
                .admin(e.isAdmin())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
