package com.learnerview.simplydone.service;

public interface RateLimiterService {

    void checkRateLimit(String userId);
}
