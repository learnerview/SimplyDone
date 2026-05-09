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
 * Sliding-window rate limiter backed by Redis sorted sets.
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
    public void checkRateLimit(String producer) {
        if (producer == null || producer.isBlank()) return;

        String key = "simplydone:ratelimit:" + producer;
        long now = System.currentTimeMillis();

        try {
            redis.opsForZSet().removeRangeByScore(key, 0, now - windowMs);

            Long count = redis.opsForZSet().zCard(key);
            if (count != null && count >= maxRequests) {
                throw new RateLimitExceededException(producer, windowMs / 1000);
            }

            redis.opsForZSet().add(key, UUID.randomUUID().toString(), now);
            redis.expire(key, Duration.ofMillis(windowMs + 1000));
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Rate limiter error for {}, allowing: {}", producer, e.getMessage());
        }
    }
}
