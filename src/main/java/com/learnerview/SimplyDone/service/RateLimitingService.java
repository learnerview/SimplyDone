package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.dto.RateLimitStatus;

/**
 * Interface for rate limiting operations.
 */
public interface RateLimitingService {
    boolean isAllowed(String userId);
    RateLimitStatus getRateLimitStatus(String userId);
}
