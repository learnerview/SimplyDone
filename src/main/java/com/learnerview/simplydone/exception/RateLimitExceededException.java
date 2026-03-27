package com.learnerview.simplydone.exception;

import lombok.Getter;

@Getter
public class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;

    public RateLimitExceededException(String producer, long retryAfterSeconds) {
        super("Rate limit exceeded for producer '" + producer + "'. Retry after " + retryAfterSeconds + "s.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
