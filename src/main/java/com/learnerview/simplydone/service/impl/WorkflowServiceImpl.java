package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.dto.JobSubmissionResponse;
import com.learnerview.simplydone.dto.WorkflowRequest;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.handler.JobHandlerRegistry;
import com.learnerview.simplydone.mapper.JobMapper;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import com.learnerview.simplydone.service.DependencyResolver;
import com.learnerview.simplydone.service.RateLimiterService;
import com.learnerview.simplydone.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles DAG workflow submissions and dependent job unblocking.
 *
 * Submit: validate handlers -> rate limit -> topological sort (cycle detection) -> persist -> enqueue roots.
 * Unblock: after a job succeeds, check if its dependents now have all deps in SUCCESS -> enqueue them.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final JobEntityRepository jobRepo;
    private final QueueRepository queueRepo;
    private final JobHandlerRegistry registry;
    private final RateLimiterService rateLimiter;
    private final DependencyResolver dependencyResolver;
    private final SchedulerProperties props;
    private final JobMapper jobMapper;

    @Override
    public List<JobSubmissionResponse> submitWorkflow(WorkflowRequest req) {
        // Validate all handlers exist
        for (WorkflowRequest.WorkflowJob wj : req.getJobs()) {
            registry.getHandler(wj.getJobType());
        }

        // Rate limit check
        rateLimiter.checkRateLimit(req.getUserId());

        // Topological sort — validates DAG, detects cycles
        List<List<String>> levels = dependencyResolver.resolve(req.getJobs());

        String workflowId = UUID.randomUUID().toString();
        JobPriority priority = jobMapper.parsePriority(req.getPriority());
        Map<String, String> localIdToJobId = new HashMap<>();
        List<JobSubmissionResponse> responses = new ArrayList<>();

        // Map local IDs to real UUIDs
        for (WorkflowRequest.WorkflowJob wj : req.getJobs()) {
            localIdToJobId.put(wj.getId(), UUID.randomUUID().toString());
        }

        // Persist all jobs
        Set<String> rootIds = new HashSet<>(levels.get(0));

        for (WorkflowRequest.WorkflowJob wj : req.getJobs()) {
            String jobId = localIdToJobId.get(wj.getId());
            String dependsOn = null;
            if (wj.getDependsOn() != null && !wj.getDependsOn().isEmpty()) {
                dependsOn = wj.getDependsOn().stream()
                        .map(localIdToJobId::get)
                        .collect(Collectors.joining(","));
            }

            boolean isRoot = rootIds.contains(wj.getId());
            Instant scheduledAt = Instant.now();

            JobEntity job = JobEntity.builder()
                    .id(jobId)
                    .jobType(wj.getJobType())
                    .status(JobStatus.QUEUED)
                    .priority(priority)
                    .payload(jobMapper.serializePayload(wj.getPayload()))
                    .userId(req.getUserId())
                    .scheduledAt(scheduledAt)
                    .maxRetries(props.getRetry().getMaxAttempts())
                    .workflowId(workflowId)
                    .dependsOn(dependsOn)
                    .build();

            jobRepo.save(job);

            // Only enqueue root jobs (no dependencies) — others wait for parents
            if (isRoot) {
                queueRepo.enqueue(jobId, priority, scheduledAt.toEpochMilli());
            }

            responses.add(JobSubmissionResponse.builder()
                    .jobId(jobId)
                    .status(JobStatus.QUEUED.name())
                    .jobType(wj.getJobType())
                    .priority(priority.name())
                    .scheduledAt(scheduledAt)
                    .build());
        }

        log.info("Workflow submitted: {} with {} jobs, {} levels",
                workflowId, req.getJobs().size(), levels.size());
        return responses;
    }

    @Override
    public void unblockDependents(JobEntity completedJob) {
        if (completedJob.getWorkflowId() == null) return;

        List<JobEntity> workflowJobs = jobRepo.findByWorkflowId(completedJob.getWorkflowId());

        // Find jobs that depend on the completed job
        for (JobEntity job : workflowJobs) {
            if (job.getDependsOn() == null || job.getStatus() != JobStatus.QUEUED) continue;

            // Check if this job depends on the completed job
            List<String> deps = List.of(job.getDependsOn().split(","));
            if (!deps.contains(completedJob.getId())) continue;

            // Check if ALL dependencies are now SUCCESS
            boolean allDone = deps.stream().allMatch(depId ->
                    workflowJobs.stream()
                            .filter(j -> j.getId().equals(depId))
                            .anyMatch(j -> j.getStatus() == JobStatus.SUCCESS));

            if (allDone) {
                queueRepo.enqueue(job.getId(), job.getPriority(), Instant.now().toEpochMilli());
                log.info("Unblocked workflow job {} (all deps complete)", job.getId());
            }
        }
    }
}
