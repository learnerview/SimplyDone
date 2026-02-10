package com.learnerview.simplydone.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(String userId, long retryAfterSeconds) {
        super("Rate limit exceeded for '" + userId + "'. Retry after " + retryAfterSeconds + "s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
