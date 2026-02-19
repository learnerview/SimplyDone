package com.learnerview.SimplyDone.exception;

/**
 * Exception for rate limit violations.
 * 
 * Thrown when a user/client exceeds their allowed request rate.
 * Typically results in HTTP 429 Too Many Requests responses.
 */
public class RateLimitException extends ApplicationException {
    
    private final long retryAfterSeconds;
    
    /**
     * Creates exception with retry information.
     */
    public RateLimitException(String message, long retryAfterSeconds) {
        super("RATE_LIMIT_EXCEEDED", message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    
    /**
     * Returns seconds to wait before retrying.
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
