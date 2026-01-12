package com.learnerview.SimplyDone.repository;

import com.learnerview.SimplyDone.config.SchedulerProperties;
import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redis-based repository following the exact pattern from redis-scheduler-master.
 * 
 * Uses Redis sorted sets with proper atomic operations and transactions.
 * Implements the same patterns as the professional reference implementation.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class JobRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, Long> redisLongTemplate;
    private final SchedulerProperties schedulerProperties;
    private final ObjectMapper objectMapper;
    
    // Redis key patterns - fully configurable from properties
    private String HIGH_PRIORITY_QUEUE;
    private String LOW_PRIORITY_QUEUE;
    private String EXECUTED_JOBS_COUNTER;
    private String REJECTED_JOBS_COUNTER;
    
    /**
     * Initialize Redis keys from configuration.
     */
    private void initializeKeys() {
        HIGH_PRIORITY_QUEUE = schedulerProperties.getQueues().getHigh();
        LOW_PRIORITY_QUEUE = schedulerProperties.getQueues().getLow();
        EXECUTED_JOBS_COUNTER = schedulerProperties.getStats().getExecuted();
        REJECTED_JOBS_COUNTER = schedulerProperties.getStats().getRejected();
    }
    
    /**
     * Stores a job in the appropriate priority queue.
     * Uses Redis sorted set with execution timestamp as score.
     * 
     * @param job the job to store
     * @return true if successfully stored, false otherwise
     */
    public boolean saveJob(Job job) {
        String queueKey = null;
        double score = 0;
        try {
            // Initialize keys if not already done
            if (HIGH_PRIORITY_QUEUE == null) {
                initializeKeys();
            }
            
            queueKey = getQueueKey(job.getPriority());
            score = job.getExecuteAt().toEpochMilli();
            String jobJson = objectMapper.writeValueAsString(job);
            
            redisTemplate.opsForZSet().add(queueKey, jobJson, score);
            log.debug("Job {} stored in {} queue with execution time {}", 
                     job.getId(), job.getPriority(), job.getExecuteAt());
            return true;
        } catch (Exception e) {
            log.error("Failed to save job {} to Redis: {} | Queue: {} | Score: {} | Error Type: {}", 
                     job.getId(), e.getMessage(), queueKey, score, e.getClass().getSimpleName());
            log.error("Full error details:", e);
            return false;
        }
    }
    
    /**
     * Retrieves the next ready job for execution using Redis transactions.
     * Follows the exact pattern from redis-scheduler-master with WATCH/MULTI/EXEC.
     * 
     * @return the next ready job, or null if no jobs are ready
     */
    public Job getNextReadyJob() {
        // Initialize keys if not already done
        if (HIGH_PRIORITY_QUEUE == null) {
            initializeKeys();
        }
        
        // First try to get a HIGH priority job
        Job job = getNextReadyJobFromQueue(HIGH_PRIORITY_QUEUE);
        if (job != null) {
            return job;
        }
        
        // If no HIGH priority jobs, try LOW priority
        return getNextReadyJobFromQueue(LOW_PRIORITY_QUEUE);
    }
    
    /**
     * Retrieves the next ready job from a specific queue using Redis transactions.
     * Implements the same atomic pattern as redis-scheduler-master.
     * 
     * @param queueKey the Redis key for the queue
     * @return the next ready job, or null if no jobs are ready
     */
    private Job getNextReadyJobFromQueue(String queueKey) {
        try {
            double currentTime = Instant.now().toEpochMilli();
            
            // Use Redis transaction for atomic operations
            return redisTemplate.execute(new SessionCallback<Job>() {
                @Override
                public Job execute(org.springframework.data.redis.core.RedisOperations operations) throws org.springframework.dao.DataAccessException {
                    // Watch the queue for changes
                    operations.watch(queueKey);
                    
                    // Get jobs with score <= current time (ready for execution)
                    Set<String> readyJobs = operations.opsForZSet()
                        .rangeByScore(queueKey, 0, currentTime, 0, 1);
                    
                    if (readyJobs == null || readyJobs.isEmpty()) {
                        operations.unwatch();
                        return null;
                    }
                    
                    // Get the first ready job
                    String jobJson = readyJobs.iterator().next();
                    
                    // Start transaction
                    operations.multi();
                    
                    // Remove the job from the queue
                    operations.opsForZSet().remove(queueKey, jobJson);
                    
                    // Execute transaction
                    List<Object> results = operations.exec();
                    
                    // Check if transaction was successful
                    if (results != null && !results.isEmpty() && (Long) results.get(0) > 0) {
                        // Job was successfully removed, parse and return it
                        try {
                            return objectMapper.readValue(jobJson, Job.class);
                        } catch (Exception e) {
                            log.error("Failed to parse job JSON: {}", e.getMessage());
                            return null;
                        }
                    } else {
                        // Race condition - job was taken by another worker
                        log.debug("Race condition detected for job in queue {}", queueKey);
                        return null;
                    }
                }
            });
            
        } catch (org.springframework.dao.DataAccessException e) {
            // Let Redis connection exceptions bubble up to the worker for proper handling
            if (e.getMessage() != null && e.getMessage().contains("Unable to connect to Redis")) {
                throw e;
            }
            log.error("Failed to retrieve next ready job from {}: {}", queueKey, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Failed to retrieve next ready job from {}: {}", queueKey, e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the size of a specific queue.
     * 
     * @param priority the job priority
     * @return number of jobs in the queue
     */
    public long getQueueSize(JobPriority priority) {
        // Initialize keys if not already done
        if (HIGH_PRIORITY_QUEUE == null) {
            initializeKeys();
        }
        
        String queueKey = getQueueKey(priority);
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size != null ? size : 0;
    }
    
    /**
     * Gets all jobs in a queue (for debugging/admin purposes).
     * 
     * @param priority the job priority
     * @return list of jobs in the queue
     */
    public List<Job> getAllJobsInQueue(JobPriority priority) {
        try {
            // Initialize keys if not already done
            if (HIGH_PRIORITY_QUEUE == null) {
                initializeKeys();
            }
            
            String queueKey = getQueueKey(priority);
            Set<String> jobs = redisTemplate.opsForZSet().range(queueKey, 0, -1);
            
            List<Job> jobList = new ArrayList<>();
            if (jobs != null) {
                for (String jobJson : jobs) {
                    try {
                        Job job = objectMapper.readValue(jobJson, Job.class);
                        jobList.add(job);
                    } catch (Exception e) {
                        log.error("Failed to parse job JSON: {}", e.getMessage());
                        // Skip invalid job and continue with others
                    }
                }
            }
            return jobList;
        } catch (Exception e) {
            log.error("Failed to retrieve jobs from {} queue: {}", priority, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Increments the executed jobs counter using proper Redis template.
     */
    public void incrementExecutedJobsCounter() {
        // Initialize keys if not already done
        if (EXECUTED_JOBS_COUNTER == null) {
            initializeKeys();
        }
        
        redisLongTemplate.opsForValue().increment(EXECUTED_JOBS_COUNTER);
    }
    
    /**
     * Increments the rejected jobs counter using proper Redis template.
     */
    public void incrementRejectedJobsCounter() {
        // Initialize keys if not already done
        if (REJECTED_JOBS_COUNTER == null) {
            initializeKeys();
        }
        
        redisLongTemplate.opsForValue().increment(REJECTED_JOBS_COUNTER);
    }
    
    /**
     * Gets the count of executed jobs using proper Redis template.
     * 
     * @return number of executed jobs
     */
    public long getExecutedJobsCount() {
        // Initialize keys if not already done
        if (EXECUTED_JOBS_COUNTER == null) {
            initializeKeys();
        }
        
        Long count = redisLongTemplate.opsForValue().get(EXECUTED_JOBS_COUNTER);
        return count != null ? count : 0;
    }
    
    /**
     * Gets the count of rejected jobs using proper Redis template.
     * 
     * @return number of rejected jobs
     */
    public long getRejectedJobsCount() {
        // Initialize keys if not already done
        if (REJECTED_JOBS_COUNTER == null) {
            initializeKeys();
        }
        
        Long count = redisLongTemplate.opsForValue().get(REJECTED_JOBS_COUNTER);
        return count != null ? count : 0;
    }
    
    /**
     * Cancels a job by removing it from the queue.
     * 
     * @param jobId the ID of the job to cancel
     * @return true if job was found and cancelled, false if not found
     */
    public boolean cancelJob(String jobId) {
        // Initialize keys if not already done
        if (HIGH_PRIORITY_QUEUE == null) {
            initializeKeys();
        }
        
        try {
            // Try to cancel from HIGH priority queue first
            if (cancelJobFromQueue(jobId, HIGH_PRIORITY_QUEUE)) {
                return true;
            }
            
            // If not found in HIGH priority, try LOW priority
            return cancelJobFromQueue(jobId, LOW_PRIORITY_QUEUE);
            
        } catch (Exception e) {
            log.error("Failed to cancel job {}: {}", jobId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Cancels a job from a specific queue.
     * 
     * @param jobId the ID of the job to cancel
     * @param queueKey the Redis key for the queue
     * @return true if job was found and cancelled, false if not found
     */
    private boolean cancelJobFromQueue(String jobId, String queueKey) {
        try {
            // Get all jobs in the queue
            Set<String> jobs = redisTemplate.opsForZSet().range(queueKey, 0, -1);
            
            if (jobs == null || jobs.isEmpty()) {
                return false;
            }
            
            // Find the job with matching ID
            for (String jobJson : jobs) {
                try {
                    Job job = objectMapper.readValue(jobJson, Job.class);
                    if (job.getId().equals(jobId)) {
                        // Remove the job from the queue
                        Long removed = redisTemplate.opsForZSet().remove(queueKey, jobJson);
                        if (removed != null && removed > 0) {
                            log.info("Job {} cancelled successfully from queue {}", jobId, queueKey);
                            return true;
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to parse job JSON while cancelling: {}", e.getMessage());
                    // Skip invalid job and continue
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Failed to cancel job {} from queue {}: {}", jobId, queueKey, e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets a specific job by ID from any queue.
     * 
     * @param jobId the ID of the job to retrieve
     * @return the job if found, null otherwise
     */
    public Job getJobById(String jobId) {
        // Initialize keys if not already done
        if (HIGH_PRIORITY_QUEUE == null) {
            initializeKeys();
        }
        
        // Try to find in HIGH priority queue first
        Job job = getJobByIdFromQueue(jobId, HIGH_PRIORITY_QUEUE);
        if (job != null) {
            return job;
        }
        
        // If not found in HIGH priority, try LOW priority
        return getJobByIdFromQueue(jobId, LOW_PRIORITY_QUEUE);
    }
    
    /**
     * Gets a specific job by ID from a specific queue.
     * 
     * @param jobId the ID of the job to retrieve
     * @param queueKey the Redis key for the queue
     * @return the job if found, null otherwise
     */
    private Job getJobByIdFromQueue(String jobId, String queueKey) {
        try {
            Set<String> jobs = redisTemplate.opsForZSet().range(queueKey, 0, -1);
            
            if (jobs == null || jobs.isEmpty()) {
                return null;
            }
            
            for (String jobJson : jobs) {
                try {
                    Job job = objectMapper.readValue(jobJson, Job.class);
                    if (job.getId().equals(jobId)) {
                        return job;
                    }
                } catch (Exception e) {
                    log.error("Failed to parse job JSON: {}", e.getMessage());
                    // Skip invalid job and continue
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Failed to retrieve job {} from queue {}: {}", jobId, queueKey, e.getMessage());
            return null;
        }
    }
    
    /**
     * Clears all jobs from both high and low priority queues.
     * Uses efficient Redis bulk delete operations.
     * 
     * @return number of jobs cleared
     */
    public int clearAllQueues() {
        // Initialize keys if not already done
        if (HIGH_PRIORITY_QUEUE == null) {
            initializeKeys();
        }
        
        try {
            // Get current queue sizes before clearing
            Long highPrioritySize = redisTemplate.opsForZSet().size(HIGH_PRIORITY_QUEUE);
            Long lowPrioritySize = redisTemplate.opsForZSet().size(LOW_PRIORITY_QUEUE);
            
            int totalCleared = 0;
            
            // Clear high priority queue
            if (highPrioritySize != null && highPrioritySize > 0) {
                redisTemplate.delete(HIGH_PRIORITY_QUEUE);
                totalCleared += highPrioritySize.intValue();
                log.info("Cleared {} jobs from high priority queue", highPrioritySize);
            }
            
            // Clear low priority queue
            if (lowPrioritySize != null && lowPrioritySize > 0) {
                redisTemplate.delete(LOW_PRIORITY_QUEUE);
                totalCleared += lowPrioritySize.intValue();
                log.info("Cleared {} jobs from low priority queue", lowPrioritySize);
            }
            
            log.info("Total jobs cleared from all queues: {}", totalCleared);
            return totalCleared;
            
        } catch (Exception e) {
            log.error("Failed to clear all queues: {}", e.getMessage());
            return 0;
        }
    }
    
    /**
     * Clears a specific priority queue.
     * 
     * @param priority the priority queue to clear
     * @return number of jobs cleared
     */
    public int clearQueue(JobPriority priority) {
        // Initialize keys if not already done
        if (HIGH_PRIORITY_QUEUE == null) {
            initializeKeys();
        }
        
        try {
            String queueKey = getQueueKey(priority);
            Long size = redisTemplate.opsForZSet().size(queueKey);
            
            if (size != null && size > 0) {
                redisTemplate.delete(queueKey);
                log.info("Cleared {} jobs from {} priority queue", size, priority);
                return size.intValue();
            }
            
            return 0;
            
        } catch (Exception e) {
            log.error("Failed to clear {} priority queue: {}", priority, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Gets the Redis key for a job priority queue.
     * 
     * @param priority the job priority
     * @return the Redis key
     */
    private String getQueueKey(JobPriority priority) {
        // Initialize keys if not already done
        if (HIGH_PRIORITY_QUEUE == null) {
            initializeKeys();
        }
        
        return switch (priority) {
            case HIGH -> HIGH_PRIORITY_QUEUE;
            case LOW -> LOW_PRIORITY_QUEUE;
        };
    }
}
