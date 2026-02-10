package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.model.JobPriority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Redis ZSET as min-heap. Score = scheduledAt epoch ms, member = jobId.
 * Atomic claim via WATCH/MULTI/EXEC (optimistic locking / CAS pattern).
 */
@Repository
@Slf4j
public class RedisQueueRepository implements QueueRepository {

    private final StringRedisTemplate redis;
    private final String queuePrefix;

    public RedisQueueRepository(StringRedisTemplate redis, SchedulerProperties props) {
        this.redis = redis;
        this.queuePrefix = props.getScheduler().getQueuePrefix();
    }

    public void enqueue(String jobId, JobPriority priority, long scheduledAtEpochMs) {
        redis.opsForZSet().add(queueKey(priority), jobId, scheduledAtEpochMs);
    }

    /**
     * Atomically claim the next ready job. WATCH/MULTI/EXEC = CAS.
     * If another worker claims it first, EXEC returns null and we skip.
     */
    @SuppressWarnings("unchecked")
    public Optional<String> claimNextReady(JobPriority priority) {
        String key = queueKey(priority);
        long now = System.currentTimeMillis();

        return redis.execute(new SessionCallback<Optional<String>>() {
            @Override
            public Optional<String> execute(RedisOperations ops) throws DataAccessException {
                ops.watch(key);
                Set<ZSetOperations.TypedTuple<String>> results =
                        ops.opsForZSet().rangeByScoreWithScores(key, 0, now, 0, 1);

                if (results == null || results.isEmpty()) {
                    ops.unwatch();
                    return Optional.empty();
                }

                String jobId = results.iterator().next().getValue();
                ops.multi();
                ops.opsForZSet().remove(key, jobId);
                List<Object> execResult = ops.exec();

                if (execResult == null || execResult.isEmpty()) {
                    return Optional.empty(); // lost race
                }
                return Optional.of(jobId);
            }
        });
    }

    public void remove(String jobId, JobPriority priority) {
        redis.opsForZSet().remove(queueKey(priority), jobId);
    }

    public long queueSize(JobPriority priority) {
        Long size = redis.opsForZSet().zCard(queueKey(priority));
        return size != null ? size : 0;
    }

    public void clearQueue(JobPriority priority) {
        redis.delete(queueKey(priority));
    }

    public void clearAll() {
        for (JobPriority p : JobPriority.values()) clearQueue(p);
    }

    private String queueKey(JobPriority priority) {
        return queuePrefix + ":" + priority.name().toLowerCase();
    }
}
