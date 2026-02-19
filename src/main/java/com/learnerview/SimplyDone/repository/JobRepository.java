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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Slf4j
public class JobRepository {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, Long> redisLongTemplate;
    private final SchedulerProperties schedulerProperties;
    private final ObjectMapper objectMapper;
    
    // Redis keys
    private String highPriorityQueue;
    private String lowPriorityQueue;
    private String executedJobsCounter;
    private String rejectedJobsCounter;
    private String deadLetterQueue;
    private String jobStatusMap;

    @jakarta.annotation.PostConstruct
    public void init() {
        highPriorityQueue = schedulerProperties.getQueues().getHigh();
        lowPriorityQueue = schedulerProperties.getQueues().getLow();
        executedJobsCounter = schedulerProperties.getStats().getExecuted();
        rejectedJobsCounter = schedulerProperties.getStats().getRejected();
        deadLetterQueue = schedulerProperties.getQueues().getDeadLetter();
        jobStatusMap = schedulerProperties.getRedis().getKeyPrefix() + ":jobs:status";
    }
    
    public void saveJob(Job job) {
        String queueKey = getQueueKey(job.getPriority());
        double score = job.getExecuteAt().toEpochMilli();
        try {
            String jobJson = objectMapper.writeValueAsString(job);
            redisTemplate.opsForZSet().add(queueKey, jobJson, score);
            
            // Also save to status map for lookup
            redisTemplate.opsForHash().put(jobStatusMap, job.getId(), jobJson);
            redisTemplate.expire(jobStatusMap, Duration.ofHours(1));
            
            log.debug("Job {} saved to {} queue and status map", job.getId(), job.getPriority());
        } catch (Exception e) {
            log.error("Failed to save job {} to {} queue: {}", job.getId(), job.getPriority(), e.getMessage());
            throw new RuntimeException("Failed to save job to queue: " + e.getMessage(), e);
        }
    }

    public void updateJobStatus(Job job) {
        try {
            String jobJson = objectMapper.writeValueAsString(job);
            redisTemplate.opsForHash().put(jobStatusMap, job.getId(), jobJson);
        } catch (Exception e) {
            log.error("Failed to update status for job {}: {}", job.getId(), e.getMessage());
        }
    }
    
    public Job getNextReadyJob(JobPriority priority) {
        return getNextReadyJobFromQueue(getQueueKey(priority));
    }

    public Job getNextReadyJob() {
        Job job = getNextReadyJob(JobPriority.HIGH);
        return job != null ? job : getNextReadyJob(JobPriority.LOW);
    }
    
    /**
     * Retrieves and claims the next ready job from the specified queue using an atomic transaction.
     * 
     * Technical approach:
     * 1. WATCH the queue key to detect concurrent modifications.
     * 2. Range query for the first job with a score <= current timestamp.
     * 3. Begin a MULTI transaction to ensure atomicity.
     * 4. REMOVE the specific job JSON from the sorted set.
     * 5. EXEC the transaction. If EXEC returns null, another instance claimed the job first.
     * 
     * @param queueKey The Redis key for the priority queue (sorted set).
     * @return The claimed Job object, or null if no jobs are ready or a transaction conflict occurred.
     */
    private Job getNextReadyJobFromQueue(String queueKey) {
        try {
            double currentTime = Instant.now().toEpochMilli();
            return redisTemplate.execute(new SessionCallback<Job>() {
                @Override
                public Job execute(org.springframework.data.redis.core.RedisOperations operations) {
                    operations.watch(queueKey);
                    Set<String> readyJobs = operations.opsForZSet().rangeByScore(queueKey, 0, currentTime, 0, 1);
                    
                    if (readyJobs == null || readyJobs.isEmpty()) {
                        operations.unwatch();
                        return null;
                    }
                    
                    String jobJson = readyJobs.iterator().next();
                    operations.multi();
                    operations.opsForZSet().remove(queueKey, jobJson);
                    List<Object> results = operations.exec();
                    if (results == null || results.isEmpty()) {
                        log.debug("Transaction conflict or no jobs ready in {} - retrying later", queueKey);
                        return null;
                    }
                    
                    if (results.get(0) instanceof Long removed && removed > 0) {
                        try {
                            log.info("Successfully claimed job from {}: {}", queueKey, jobJson);
                            return objectMapper.readValue(jobJson, Job.class);
                        } catch (Exception e) {
                            log.error("Failed to parse claimed job JSON: {}", e.getMessage());
                            return null;
                        }
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("connect")) throw e;
            log.error("Queue retrieval error: {}", e.getMessage());
            return null;
        }
    }
    
    public long getQueueSize(JobPriority priority) {
        String queueKey = getQueueKey(priority);
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size != null ? size : 0;
    }
    
    public List<Job> getAllJobsInQueue(JobPriority priority) {
        try {
            String queueKey = getQueueKey(priority);
            Set<String> jobs = redisTemplate.opsForZSet().range(queueKey, 0, -1);
            List<Job> jobList = new ArrayList<>();
            if (jobs != null) {
                for (String jobJson : jobs) {
                    try {
                        jobList.add(objectMapper.readValue(jobJson, Job.class));
                    } catch (Exception e) {
                        log.warn("Skipping malformed job JSON in queue: {}", e.getMessage());
                    }
                }
            }
            return jobList;
        } catch (Exception e) {
            log.error("Failed to list jobs: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    public void incrementExecutedJobsCounter() {
        redisLongTemplate.opsForValue().increment(executedJobsCounter);
    }
    
    public void incrementRejectedJobsCounter() {
        redisLongTemplate.opsForValue().increment(rejectedJobsCounter);
    }
    
    public long getExecutedJobsCount() {
        Long count = redisLongTemplate.opsForValue().get(executedJobsCounter);
        return count != null ? count : 0;
    }
    
    public long getRejectedJobsCount() {
        Long count = redisLongTemplate.opsForValue().get(rejectedJobsCounter);
        return count != null ? count : 0;
    }
    
    public boolean deleteJob(String jobId) {
        return deleteJobFromQueue(jobId, highPriorityQueue) || deleteJobFromQueue(jobId, lowPriorityQueue);
    }

    public boolean deleteJob(String jobId, JobPriority priority) {
        return deleteJobFromQueue(jobId, getQueueKey(priority));
    }
    
    private boolean deleteJobFromQueue(String jobId, String queueKey) {
        try {
            Set<String> jobs = redisTemplate.opsForZSet().range(queueKey, 0, -1);
            if (jobs == null) return false;
            for (String jobJson : jobs) {
                Job job = objectMapper.readValue(jobJson, Job.class);
                if (job.getId().equals(jobId)) {
                    Long removed = redisTemplate.opsForZSet().remove(queueKey, jobJson);
                    return removed != null && removed > 0;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete job {} from queue {}: {}", jobId, queueKey, e.getMessage());
            throw new RuntimeException("Failed to delete job from queue: " + e.getMessage(), e);
        }
    }
    
    public Job getJobById(String jobId) {
        try {
            Object jobJson = redisTemplate.opsForHash().get(jobStatusMap, jobId);
            if (jobJson != null) {
                return objectMapper.readValue(jobJson.toString(), Job.class);
            }
        } catch (Exception e) {
            log.error("Error retrieving job {} from status map: {}", jobId, e.getMessage());
        }
        
        // Fallback to queue search if not in map (though it should be)
        Job job = getJobByIdFromQueue(jobId, highPriorityQueue);
        return job != null ? job : getJobByIdFromQueue(jobId, lowPriorityQueue);
    }
    
    private Job getJobByIdFromQueue(String jobId, String queueKey) {
        try {
            Set<String> jobs = redisTemplate.opsForZSet().range(queueKey, 0, -1);
            if (jobs == null) return null;
            for (String jobJson : jobs) {
                Job job = objectMapper.readValue(jobJson, Job.class);
                if (job.getId().equals(jobId)) return job;
            }
        } catch (Exception e) {
            log.error("Failed to retrieve job {} from queue {}: {}", jobId, queueKey, e.getMessage());
            throw new RuntimeException("Failed to retrieve job from queue: " + e.getMessage(), e);
        }
        return null;
    }
    
    public int clearAllQueues() {
        return clearQueue(JobPriority.HIGH) + clearQueue(JobPriority.LOW);
    }
    
    public int clearQueue(JobPriority priority) {
        try {
            String queueKey = getQueueKey(priority);
            Long size = redisTemplate.opsForZSet().size(queueKey);
            if (size != null && size > 0) {
                redisTemplate.delete(queueKey);
                return size.intValue();
            }
            return 0;
        } catch (Exception e) {
            log.error("Failed to clear {} priority queue: {}", priority, e.getMessage());
            throw new RuntimeException("Failed to clear queue: " + e.getMessage(), e);
        }
    }
    
    public void saveToDeadLetterQueue(String deadLetterJson) {
        redisTemplate.opsForZSet().add(deadLetterQueue, deadLetterJson, Instant.now().toEpochMilli());
    }

    public Set<String> getDeadLetterJobsRaw() {
        return redisTemplate.opsForZSet().range(deadLetterQueue, 0, -1);
    }

    public int clearDeadLetterQueue() {
        Long count = redisTemplate.opsForZSet().size(deadLetterQueue);
        if (count != null && count > 0) {
            redisTemplate.delete(deadLetterQueue);
            return count.intValue();
        }
        return 0;
    }

    public void removeFromDeadLetterQueue(String jobJson) {
        redisTemplate.opsForZSet().remove(deadLetterQueue, jobJson);
    }

    private String getQueueKey(JobPriority priority) {
        return priority == JobPriority.HIGH ? highPriorityQueue : lowPriorityQueue;
    }
}
