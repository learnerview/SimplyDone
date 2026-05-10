package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.model.JobPriority;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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
 * Redis-backed priority queue using score-ordered job IDs.
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

    @Retry(name = "redisQueue")
    @CircuitBreaker(name = "redisQueue")
    public void enqueue(String jobId, JobPriority priority, long scheduledAtEpochMs) {
        redis.opsForZSet().add(queueKey(priority), jobId, scheduledAtEpochMs);
    }

    /**
     * Claims the next ready job with optimistic locking.
     */
    @SuppressWarnings("unchecked")
    @Retry(name = "redisQueue")
    @CircuitBreaker(name = "redisQueue")
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
                    return Optional.empty();
                }
                return Optional.of(jobId);
            }
        });
    }

    @Retry(name = "redisQueue")
    @CircuitBreaker(name = "redisQueue")
    public void remove(String jobId, JobPriority priority) {
        redis.opsForZSet().remove(queueKey(priority), jobId);
    }

    @Retry(name = "redisQueue")
    @CircuitBreaker(name = "redisQueue")
    public long queueSize(JobPriority priority) {
        Long size = redis.opsForZSet().zCard(queueKey(priority));
        return size != null ? size : 0;
    }

    @Retry(name = "redisQueue")
    @CircuitBreaker(name = "redisQueue")
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
