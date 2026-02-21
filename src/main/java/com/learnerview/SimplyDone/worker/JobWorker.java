package com.learnerview.SimplyDone.worker;

import com.learnerview.SimplyDone.config.SchedulerProperties;
import com.learnerview.SimplyDone.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Background worker that processes jobs from the Redis queues.
 * 
 * This worker runs on a fixed schedule and:
 * 1. Checks for ready jobs in priority order (HIGH first, then LOW)
 * 2. Executes one job at a time to maintain order
 * 3. Updates statistics and logs execution results
 * 4. Handles Redis connection failures gracefully
 * 
 * The worker is designed to be lightweight and efficient,
 * processing jobs as soon as they become ready for execution.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobWorker {

    private final JobService jobService;
    private final SchedulerProperties schedulerProperties;

    // Internal monitoring counters
    private final AtomicLong workerCycles = new AtomicLong(0);
    private final AtomicLong jobsExecuted = new AtomicLong(0);
    private final AtomicLong lastExecutionTime = new AtomicLong(0);
    private final AtomicLong consecutiveRedisFailures = new AtomicLong(0);
    
    /**
     * Main worker method that runs on a fixed schedule.
     * Drains up to {@code maxJobsPerCycle} jobs per tick for better throughput.
     */
    @Scheduled(fixedRateString = "#{schedulerProperties.worker.intervalMs}")
    public void processJobs() {
        long cycles = workerCycles.incrementAndGet();
        long startTime = System.currentTimeMillis();
        int maxPerCycle = schedulerProperties.getWorker().getMaxJobsPerCycle();

        try {
            int processed = 0;
            while (processed < maxPerCycle) {
                boolean jobExecuted = jobService.executeNextReadyJob();
                if (!jobExecuted) break;
                jobsExecuted.incrementAndGet();
                consecutiveRedisFailures.set(0);
                processed++;
            }

            if (processed > 0) {
                lastExecutionTime.set(System.currentTimeMillis() - startTime);
                log.debug("Worker cycle {}: {} job(s) executed in {}ms", cycles, processed,
                        lastExecutionTime.get());
            } else if (cycles % 60 == 0) {
                log.debug("Worker cycle {}: No jobs ready", cycles);
            }

            if (cycles % 60 == 0) {
                logPerformance(startTime);
            }

        } catch (DataAccessException e) {
            long failures = consecutiveRedisFailures.incrementAndGet();
            log.error("Redis connection error (failure #{}): {}", failures, e.getMessage());
            if (failures > 10) {
                log.error("Critical: {} consecutive Redis failures detected", failures);
            }
        } catch (Exception e) {
            log.error("Error in worker cycle {}: {}", cycles, e.getMessage(), e);
        }
    }

    /**
     * Logs performance metrics periodically.
     */
    private void logPerformance(long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        log.info("Worker performance - Cycles: {}, Jobs Executed: {}, Last Execution: {}ms, Redis Failures: {}",
                workerCycles.get(), jobsExecuted.get(), lastExecutionTime.get(), consecutiveRedisFailures.get());
    }

    /**
     * Gets current worker statistics.
     */
    public WorkerStats getStats() {
        return new WorkerStats(
                workerCycles.get(),
                jobsExecuted.get(),
                lastExecutionTime.get(),
                schedulerProperties.getWorker().getIntervalMs(),
                consecutiveRedisFailures.get()
        );
    }
    
    /**
     * Data class representing worker statistics.
     */
    public record WorkerStats(
        long totalCycles,
        long jobsExecuted,
        long lastExecutionTime,
        long intervalMs,
        long redisFailures
    ) {}
}
