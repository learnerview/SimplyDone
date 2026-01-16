package com.learnerview.SimplyDone.worker;

import com.learnerview.SimplyDone.config.SchedulerProperties;
import com.learnerview.SimplyDone.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    
    // Statistics tracking
    private long totalJobsExecuted = 0;
    private long workerCycles = 0;
    private long lastExecutionTime = 0;
    
    // Redis connection health tracking
    private long consecutiveRedisFailures = 0;
    private static final long MAX_REDIS_FAILURES_BEFORE_LOG_REDUCTION = 5;
    private long lastRedisFailureLogTime = 0;
    private static final long REDIS_FAILURE_LOG_INTERVAL_MS = 30000; // Log every 30 seconds
    
    /**
     * Main worker method that runs on a fixed schedule.
     * 
     * This method is called every second (configurable) to check for
     * and execute ready jobs. It maintains the priority order by
     * always checking HIGH priority jobs first.
     */
    @Scheduled(fixedRateString = "#{schedulerProperties.worker.intervalMs}")
    public void processJobs() {
        workerCycles++;
        long startTime = System.currentTimeMillis();
        
        try {
            // Attempt to execute the next ready job
            boolean jobExecuted = jobService.executeNextReadyJob();
            
            // Reset Redis failure counter on successful execution
            if (jobExecuted) {
                totalJobsExecuted++;
                lastExecutionTime = System.currentTimeMillis();
                consecutiveRedisFailures = 0;
                log.info("Worker cycle {}: Job executed successfully (Total: {})", 
                        workerCycles, totalJobsExecuted);
            } else {
                // Log debug message every 60 cycles to reduce noise
                if (workerCycles % 60 == 0) {
                    log.debug("Worker cycle {}: No jobs ready for execution", workerCycles);
                }
            }
            
            // Log performance metrics every 60 cycles (approximately every minute)
            if (workerCycles % 60 == 0) {
                logPerformanceMetrics(startTime);
            }
            
        } catch (DataAccessException e) {
            handleRedisConnectionFailure(e);
        } catch (Exception e) {
            log.error("Error in worker cycle {}: {}", workerCycles, e.getMessage(), e);
        }
    }
    
    /**
     * Handles Redis connection failures with intelligent logging to reduce noise.
     * 
     * @param e the Redis connection exception
     */
    private void handleRedisConnectionFailure(DataAccessException e) {
        consecutiveRedisFailures++;
        long currentTime = System.currentTimeMillis();
        
        // Only log the first few failures, then log periodically
        if (consecutiveRedisFailures <= MAX_REDIS_FAILURES_BEFORE_LOG_REDUCTION || 
            (currentTime - lastRedisFailureLogTime) > REDIS_FAILURE_LOG_INTERVAL_MS) {
            
            log.warn("Redis connection failure #{}: Unable to connect to Redis. Worker will retry...", 
                    consecutiveRedisFailures);
            lastRedisFailureLogTime = currentTime;
            
            // Only show full stack trace for the first failure
            if (consecutiveRedisFailures == 1) {
                log.debug("Redis connection details", e);
            }
        }
        
        // Reset counter if connection is restored (this will happen when a job executes successfully)
    }
    
    /**
     * Logs performance and statistics information.
     * 
     * @param startTime the start time of the current cycle
     */
    private void logPerformanceMetrics(long startTime) {
        long cycleTime = System.currentTimeMillis() - startTime;
        long timeSinceLastExecution = lastExecutionTime > 0 ? 
            System.currentTimeMillis() - lastExecutionTime : -1;
        
        log.info("📊 Worker Statistics - Cycles: {}, Jobs Executed: {}, " +
                "Cycle Time: {}ms, Time Since Last Execution: {}s, Redis Failures: {}",
                workerCycles, totalJobsExecuted, cycleTime, 
                timeSinceLastExecution > 0 ? timeSinceLastExecution / 1000 : -1,
                consecutiveRedisFailures);
    }
    
    /**
     * Gets current worker statistics.
     * 
     * @return WorkerStats object with current statistics
     */
    public WorkerStats getStats() {
        return new WorkerStats(
            workerCycles,
            totalJobsExecuted,
            lastExecutionTime,
            schedulerProperties.getWorker().getIntervalMs(),
            consecutiveRedisFailures
        );
    }
    
    /**
     * Data class representing worker statistics.
     */
    public record WorkerStats(
        long totalCycles,        // Total number of worker cycles
        long jobsExecuted,       // Total jobs executed by this worker
        long lastExecutionTime,  // Timestamp of last job execution
        long intervalMs,         // Worker interval in milliseconds
        long redisFailures       // Consecutive Redis connection failures
    ) {}
}
