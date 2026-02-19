package com.learnerview.SimplyDone.service.impl;

import com.learnerview.SimplyDone.config.SchedulerProperties;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.service.RateLimitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
            List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                public List<Object> execute(org.springframework.data.redis.core.RedisOperations operations) throws org.springframework.dao.DataAccessException {
                    operations.multi();
                    operations.opsForValue().get(rateLimitKey);
                    operations.opsForValue().increment(rateLimitKey);
                    operations.expire(rateLimitKey, Duration.ofMinutes(1));
                    return operations.exec();
                }
            });
            
            if (results != null && results.size() >= 2) {
                Long newCount = (Long) results.get(1);
                int requestCount = newCount != null ? newCount.intValue() : 1;
                log.debug("Rate limit check for user {}: {}/{}", userId, requestCount, maxRequests);
                return requestCount < maxRequests;
            }
            return true;
        } catch (Exception e) {
            log.error("Rate limit check failed for user {} - denying request for safety: {}", userId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public RateLimitStatus getRateLimitStatus(String userId) {
        int maxRequests = schedulerProperties.getRateLimit().getRequestsPerMinute();
        String rateLimitKey = getRateLimitKey(userId);
        
        try {
            String currentCountStr = redisTemplate.opsForValue().get(rateLimitKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            Long ttl = redisTemplate.getExpire(rateLimitKey);
            long resetTimeSeconds = ttl != null && ttl > 0 ? ttl : 60;
            
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
