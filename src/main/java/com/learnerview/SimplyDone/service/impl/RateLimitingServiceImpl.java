package com.learnerview.SimplyDone.service.impl;

import com.learnerview.SimplyDone.config.SchedulerProperties;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.service.RateLimitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Implementation of RateLimitingService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingServiceImpl implements RateLimitingService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final SchedulerProperties schedulerProperties;
    
    @Override
    public boolean isAllowed(String userId) {
        int maxRequests = schedulerProperties.getRateLimit().getRequestsPerMinute();
        String rateLimitKey = getRateLimitKey(userId);

        try {
            // INCR is atomic — each call increments and returns the new counter value.
            // On the very first request in a window (count == 1) we set a 60-second TTL
            // so the key auto-expires at the end of the fixed minute window.
            Long count = redisTemplate.opsForValue().increment(rateLimitKey);
            if (count != null && count == 1) {
                redisTemplate.expire(rateLimitKey, Duration.ofMinutes(1));
            }
            int requestCount = count != null ? count.intValue() : 1;
            log.debug("Rate limit check for user {}: {}/{}", userId, requestCount, maxRequests);
            return requestCount <= maxRequests;
        } catch (Exception e) {
            log.warn("Rate limit check failed for user {} - allowing request (Redis unavailable): {}", userId, e.getMessage());
            return true;
        }
    }
    
    @Override
    public RateLimitStatus getRateLimitStatus(String userId) {
        int maxRequests = schedulerProperties.getRateLimit().getRequestsPerMinute();
        String rateLimitKey = getRateLimitKey(userId);
        
        try {
            String currentCountStr = redisTemplate.opsForValue().get(rateLimitKey);
            int currentCount = 0;
            if (currentCountStr != null) {
                try {
                    currentCount = Integer.parseInt(currentCountStr);
                } catch (NumberFormatException e) {
                    log.warn("Invalid rate limit counter for user {}: {}", userId, currentCountStr);
                }
            }
            Long ttl = redisTemplate.getExpire(rateLimitKey);
            long resetTimeSeconds = ttl != null && ttl > 0 ? ttl : 60;
            // allowed = "can another request be made without exceeding the limit?"
            // Uses < (not <=) because isAllowed() INCRs first, so at currentCount==maxRequests
            // the next INCR would produce maxRequests+1 which is denied.
            return new RateLimitStatus(currentCount, maxRequests, resetTimeSeconds, currentCount < maxRequests);
        } catch (Exception e) {
            log.error("Error getting rate limit status for user {}: {}", userId, e.getMessage());
            return new RateLimitStatus(0, maxRequests, 60, true);
        }
    }
    
    private String getRateLimitKey(String userId) {
        long currentMinute = Instant.now().getEpochSecond() / 60;
        return String.format("rate_limit:%s:%d", userId, currentMinute);
    }
}
