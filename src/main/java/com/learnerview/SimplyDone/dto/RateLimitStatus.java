package com.learnerview.SimplyDone.dto;

// sent back to the client to tell them how their rate limit is looking
public record RateLimitStatus(
        int currentCount,       // how many requests this user has made in the current window
        int maxRequests,        // max requests allowed per minute
        long resetTimeSeconds,  // when the window resets (unix timestamp)
        boolean allowed         // false if they've hit the limit
) {}
