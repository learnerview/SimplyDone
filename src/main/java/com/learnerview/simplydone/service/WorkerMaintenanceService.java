package com.learnerview.simplydone.service;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import com.learnerview.simplydone.service.RetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Profile("worker")
@Slf4j
@RequiredArgsConstructor
public class WorkerMaintenanceService {

    private final JobEntityRepository jobRepo;
    private final QueueRepository queueRepo;
    private final RetryService retryService;
    private final SchedulerProperties props;

    @Scheduled(fixedDelayString = "${simplydone.worker.retry-promoter-interval-ms:1000}")
    public void promoteRetries() {
        List<JobEntity> due = jobRepo.findTop100ByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(
                JobStatus.RETRY_SCHEDULED, Instant.now());

        for (JobEntity job : due) {
            job.setStatus(JobStatus.QUEUED);
            jobRepo.save(job);
            queueRepo.enqueue(job.getId(), job.getPriority(), job.getNextRunAt().toEpochMilli());
        }
    }

    @Scheduled(fixedDelayString = "${simplydone.worker.lease-reaper-interval-ms:5000}")
    public void recoverExpiredLeases() {
        List<JobEntity> expired = jobRepo.findTop100ByStatusAndVisibleAtBeforeOrderByVisibleAtAsc(
                JobStatus.RUNNING, Instant.now());

        for (JobEntity job : expired) {
            log.warn("Recovering expired lease for job {}", job.getId());
            retryService.handleFailure(job, "Worker lease expired", 0L);
        }
    }
}
