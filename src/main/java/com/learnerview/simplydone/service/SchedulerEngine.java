package com.learnerview.simplydone.service;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.model.JobPriority;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Deficit Round-Robin scheduler.
 *
 * Each priority has a weight (default HIGH=70, NORMAL=20, LOW=10).
 * Each cycle:
 *   1. Add weight to each priority's deficit counter
 *   2. Pick the priority with the highest deficit that has a ready job
 *   3. Subtract totalWeight from the picked priority's deficit
 *
 * This guarantees weighted fairness: ~70% HIGH, ~20% NORMAL, ~10% LOW.
 * Starvation is impossible — deficit accumulates for idle queues.
 *
 * Time per cycle: O(P) where P = number of priority levels (3 = constant).
 */
@Service
@Slf4j
public class SchedulerEngine {

    private final QueueRepository queueRepo;
    private final JobEntityRepository jobRepo;
    private final JobExecutorService executor;

    private final JobPriority[] priorities = JobPriority.values();
    private final int[] weights;
    private final int[] deficit;
    private final int totalWeight;

    public SchedulerEngine(QueueRepository queueRepo, JobEntityRepository jobRepo,
                           JobExecutorService executor, SchedulerProperties props) {
        this.queueRepo = queueRepo;
        this.jobRepo = jobRepo;
        this.executor = executor;

        this.weights = new int[]{
                props.getScheduler().getWeights().getHigh(),
                props.getScheduler().getWeights().getNormal(),
                props.getScheduler().getWeights().getLow()
        };
        this.deficit = new int[priorities.length];
        this.totalWeight = weights[0] + weights[1] + weights[2];
    }

    @Scheduled(fixedDelayString = "${simplydone.scheduler.polling-interval-ms:1000}")
    public void poll() {
        // Step 1: Add weights to deficit counters
        for (int i = 0; i < priorities.length; i++) {
            deficit[i] += weights[i];
        }

        // Step 2: Pick priority with max deficit that has a ready job
        int bestIdx = -1;
        int bestDeficit = Integer.MIN_VALUE;

        for (int i = 0; i < priorities.length; i++) {
            if (deficit[i] > bestDeficit && queueRepo.queueSize(priorities[i]) > 0) {
                bestDeficit = deficit[i];
                bestIdx = i;
            }
        }

        if (bestIdx == -1) return; // no ready jobs

        // Step 3: Atomic claim from the selected queue
        Optional<String> claimed = queueRepo.claimNextReady(priorities[bestIdx]);
        if (claimed.isEmpty()) return;

        // Step 4: Deduct deficit
        deficit[bestIdx] -= totalWeight;

        // Step 5: Execute
        String jobId = claimed.get();
        jobRepo.findById(jobId).ifPresentOrElse(
                executor::execute,
                () -> log.warn("Claimed job {} not found in DB", jobId)
        );
    }
}
