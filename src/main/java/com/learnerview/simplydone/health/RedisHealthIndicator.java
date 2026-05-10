package com.learnerview.simplydone.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final StringRedisTemplate redis;

    public RedisHealthIndicator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Health health() {
        try {
            String pong = redis.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return Health.up().withDetail("redis", "PONG").build();
            }
            return Health.down().withDetail("redis", pong).build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
