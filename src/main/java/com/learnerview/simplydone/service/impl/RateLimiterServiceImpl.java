package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.exception.RateLimitExceededException;
import com.learnerview.simplydone.service.RateLimiterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Sliding Window rate limiter using Redis ZSET.
 *
 * Each user gets a ZSET where:
 *   member = unique request ID
 *   score  = timestamp (epoch ms)
 *
 * On check: evict entries outside window, count remaining, reject if over limit.
 * Time complexity: O(log N) for ZADD/ZREMRANGEBYSCORE, O(1) for ZCARD.
 */
@Service
@Slf4j
public class RateLimiterServiceImpl implements RateLimiterService {

    private final StringRedisTemplate redis;
    private final int maxRequests;
    private final long windowMs;

    public RateLimiterServiceImpl(StringRedisTemplate redis, SchedulerProperties props) {
        this.redis = redis;
        this.maxRequests = props.getRateLimit().getRequestsPerMinute();
        this.windowMs = props.getRateLimit().getWindowSeconds() * 1000L;
    }

    @Override
    public void checkRateLimit(String userId) {
        if (userId == null || userId.isBlank()) return;

        String key = "simplydone:ratelimit:" + userId;
        long now = System.currentTimeMillis();

        try {
            // Evict entries outside the sliding window
            redis.opsForZSet().removeRangeByScore(key, 0, now - windowMs);

            Long count = redis.opsForZSet().zCard(key);
            if (count != null && count >= maxRequests) {
                throw new RateLimitExceededException(userId, windowMs / 1000);
            }

            // Record this request
            redis.opsForZSet().add(key, UUID.randomUUID().toString(), now);
            redis.expire(key, Duration.ofMillis(windowMs + 1000));
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Rate limiter error for {}, allowing: {}", userId, e.getMessage());
        }
    }
}
