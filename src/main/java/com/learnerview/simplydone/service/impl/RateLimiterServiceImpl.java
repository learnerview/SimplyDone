package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.config.SchedulerProperties;
import com.learnerview.simplydone.exception.RateLimitExceededException;
import com.learnerview.simplydone.service.RateLimiterService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.lettuce.core.RedisCommandTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window rate limiter backed by Redis sorted sets.
 */
@Service
@Slf4j
public class RateLimiterServiceImpl implements RateLimiterService {

    private final StringRedisTemplate redis;
    private final int maxRequests;
    private final long windowMs;
    private final Retry redisRetry;
    private final CircuitBreaker redisCircuitBreaker;

    public RateLimiterServiceImpl(StringRedisTemplate redis, SchedulerProperties props) {
        this.redis = redis;
        this.maxRequests = props.getRateLimit().getRequestsPerMinute();
        this.windowMs = props.getRateLimit().getWindowSeconds() * 1000L;
        this.fallbackLimit = Math.max(1, this.maxRequests / 10);
        this.redisRetry = Retry.of("redisRateLimiter",
            RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(200))
                .build());
        this.redisCircuitBreaker = CircuitBreaker.of("redisRateLimiter",
            CircuitBreakerConfig.custom()
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
    }

    // Simple in-memory fallback counters used when Redis is unavailable.
    private final ConcurrentHashMap<String, AtomicInteger> fallbackCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> fallbackResetAt = new ConcurrentHashMap<>();
    private final int fallbackLimit;

    @Override
    public void checkRateLimit(String producer) {
        if (producer == null || producer.isBlank()) return;

        String key = "simplydone:ratelimit:" + producer;
        long now = System.currentTimeMillis();

        try {
            Runnable guarded = CircuitBreaker.decorateRunnable(redisCircuitBreaker,
                    Retry.decorateRunnable(redisRetry, () -> runRedisRateLimit(key, producer, now)));
            guarded.run();
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (RedisCommandTimeoutException rte) {
            // Redis timed out — use a conservative in-memory fallback to avoid
            // unbounded acceptance that can overwhelm downstream systems.
            log.warn("Rate limiter Redis timeout for {}, using in-memory fallback: {}", producer, rte.getMessage());
            useFallbackRateLimit(producer, now);
        } catch (Exception e) {
            log.warn("Rate limiter error for {}, allowing (fallback): {}", producer, e.getMessage());
            useFallbackRateLimit(producer, now);
        }
    }

    private void runRedisRateLimit(String key, String producer, long now) {
        redis.opsForZSet().removeRangeByScore(key, 0, now - windowMs);

        Long count = redis.opsForZSet().zCard(key);
        if (count != null && count >= maxRequests) {
            throw new RateLimitExceededException(producer, windowMs / 1000);
        }

        redis.opsForZSet().add(key, UUID.randomUUID().toString(), now);
        redis.expire(key, Duration.ofMillis(windowMs + 1000));
    }

    private void useFallbackRateLimit(String producer, long now) {
        long resetAt = fallbackResetAt.compute(producer, (k, v) -> {
            long next = (v == null || v < now) ? now + windowMs : v;
            return next;
        });

        if (fallbackResetAt.get(producer) < now) {
            // reset counter and window
            fallbackCounters.put(producer, new AtomicInteger(0));
            fallbackResetAt.put(producer, now + windowMs);
        }

        AtomicInteger counter = fallbackCounters.computeIfAbsent(producer, k -> new AtomicInteger(0));
        int cur = counter.incrementAndGet();
        if (cur > fallbackLimit) {
            throw new RateLimitExceededException(producer, windowMs / 1000);
        }
    }
}
