package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.config.SchedulerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Rate limiting service following the exact pattern from Rate-Limiter-master.
 * 
 * Uses Redis with atomic operations for distributed rate limiting.
 * Implements fixed window algorithm with proper Redis transactions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitingService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final SchedulerProperties schedulerProperties;
    
    /**
     * Checks if a user is allowed to submit a job based on rate limits.
     * Uses Redis atomic operations to ensure thread safety.
     * 
     * @param userId the user identifier
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String userId) {
        int maxRequests = schedulerProperties.getRateLimit().getRequestsPerMinute();
        String rateLimitKey = getRateLimitKey(userId);
        
        try {
            // Use Redis transaction for atomic operations
            List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                public List<Object> execute(org.springframework.data.redis.core.RedisOperations operations) throws org.springframework.dao.DataAccessException {
                    operations.multi();
                    
                    // Get current count
                    operations.opsForValue().get(rateLimitKey);
                    
                    // Increment counter
                    operations.opsForValue().increment(rateLimitKey);
                    
                    // Set expiration if key is new
                    operations.expire(rateLimitKey, Duration.ofMinutes(1));
                    
                    return operations.exec();
                }
            });
            
            if (results != null && results.size() >= 2) {
                String currentCountStr = (String) results.get(0);
                Long newCount = (Long) results.get(1);
                
                int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
                
                // If this was the first request (currentCount is null), newCount will be 1
                // If there were already requests, newCount will be currentCount + 1
                int requestCount = newCount != null ? newCount.intValue() : 1;
                
                log.debug("Rate limit check for user {}: {}/{}", userId, requestCount, maxRequests);
                
                return requestCount <= maxRequests;
            }
            
            // Fallback - allow request if Redis operation fails
            return true;
            
        } catch (Exception e) {
            log.error("Error checking rate limit for user {}: {}", userId, e.getMessage());
            // Fail open - allow the request if rate limiting fails
            return true;
        }
    }
    
    /**
     * Gets the current rate limit status for a user.
     * 
     * @param userId the user identifier
     * @return RateLimitStatus object with current usage
     */
    public RateLimitStatus getRateLimitStatus(String userId) {
        int maxRequests = schedulerProperties.getRateLimit().getRequestsPerMinute();
        String rateLimitKey = getRateLimitKey(userId);
        
        try {
            String currentCountStr = redisTemplate.opsForValue().get(rateLimitKey);
            int currentCount = currentCountStr != null ? Integer.parseInt(currentCountStr) : 0;
            
            Long ttl = redisTemplate.getExpire(rateLimitKey);
            long resetTimeSeconds = ttl != null && ttl > 0 ? ttl : 60;
            
            return new RateLimitStatus(
                currentCount,
                maxRequests,
                resetTimeSeconds,
                currentCount < maxRequests
            );
            
        } catch (Exception e) {
            log.error("Error getting rate limit status for user {}: {}", userId, e.getMessage());
            return new RateLimitStatus(0, maxRequests, 60, true);
        }
    }
    
    /**
     * Generates the Redis key for rate limiting.
     * Uses fixed window approach with minute-based windows.
     * 
     * @param userId the user identifier
     * @return the Redis key for the current minute window
     */
    private String getRateLimitKey(String userId) {
        // Use the current minute as the window to ensure fixed window behavior
        long currentMinute = Instant.now().getEpochSecond() / 60;
        return String.format("rate_limit:%s:%d", userId, currentMinute);
    }
    
    /**
     * Data class representing rate limit status.
     */
    public record RateLimitStatus(
        int currentCount,      // Current number of requests in the window
        int maxRequests,       // Maximum allowed requests
        long resetTimeSeconds, // Seconds until window resets
        boolean allowed        // Whether new requests are currently allowed
    ) {}
}
