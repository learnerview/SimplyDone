package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.config.SchedulerProperties;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.service.impl.RateLimitingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitingService Tests")
class RateLimitingServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RateLimitingServiceImpl rateLimitingService;

    @BeforeEach
    void setUp() {
        SchedulerProperties props = new SchedulerProperties();
        props.getRateLimit().setRequestsPerMinute(5);
        rateLimitingService = new RateLimitingServiceImpl(redisTemplate, props);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("isAllowed returns true when under the limit")
    void isAllowed_underLimit_returnsTrue() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        assertThat(rateLimitingService.isAllowed("user-1")).isTrue();
    }

    @Test
    @DisplayName("isAllowed sets TTL on first request in a window (duration <= 60s)")
    void isAllowed_firstRequest_setsTtl() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        rateLimitingService.isAllowed("user-1");

        // B6 fix: TTL is now the remaining time in the current 60-second window (1–60s),
        // not always a flat 60 seconds.
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(redisTemplate).expire(anyString(), ttlCaptor.capture());
        Duration ttl = ttlCaptor.getValue();
        assertThat(ttl.getSeconds()).isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    @DisplayName("isAllowed does not reset TTL on subsequent requests")
    void isAllowed_subsequentRequest_doesNotResetTtl() {
        when(valueOps.increment(anyString())).thenReturn(3L);

        rateLimitingService.isAllowed("user-1");

        verify(redisTemplate, never()).expire(anyString(), any());
    }

    @Test
    @DisplayName("isAllowed returns true at exactly the rate limit")
    void isAllowed_atLimit_returnsTrue() {
        when(valueOps.increment(anyString())).thenReturn(5L); // exactly maxRequests

        assertThat(rateLimitingService.isAllowed("user-1")).isTrue();
    }

    @Test
    @DisplayName("isAllowed returns false when limit is exceeded")
    void isAllowed_overLimit_returnsFalse() {
        when(valueOps.increment(anyString())).thenReturn(6L); // one over maxRequests=5

        assertThat(rateLimitingService.isAllowed("user-1")).isFalse();
    }

    @Test
    @DisplayName("isAllowed returns true when Redis is unavailable (fail-open)")
    void isAllowed_redisUnavailable_failsOpen() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("Redis down"));

        assertThat(rateLimitingService.isAllowed("user-1")).isTrue();
    }

    @Test
    @DisplayName("isAllowed uses per-user keys (different users are independent)")
    void isAllowed_differentUsers_useIndependentCounters() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        rateLimitingService.isAllowed("user-a");
        rateLimitingService.isAllowed("user-b");

        // Each user gets their own key — verify two separate INCR calls were made
        verify(valueOps, times(2)).increment(anyString());
    }

    @Test
    @DisplayName("getRateLimitStatus returns correct count and remaining time")
    void getRateLimitStatus_returnsCorrectInfo() {
        when(valueOps.get(anyString())).thenReturn("3");
        when(redisTemplate.getExpire(anyString())).thenReturn(45L);

        RateLimitStatus status = rateLimitingService.getRateLimitStatus("user-1");

        assertThat(status.currentCount()).isEqualTo(3);
        assertThat(status.maxRequests()).isEqualTo(5);
        assertThat(status.resetTimeSeconds()).isEqualTo(45L);
        // 3 < 5 → at least one more request can be made
        assertThat(status.allowed()).isTrue();
    }

    @Test
    @DisplayName("getRateLimitStatus returns allowed=false when counter equals the limit (next INCR would exceed it)")
    void getRateLimitStatus_atLimit_notAllowed() {
        // stored count = maxRequests = 5 → next isAllowed() call will INCR to 6 and deny
        when(valueOps.get(anyString())).thenReturn("5");
        when(redisTemplate.getExpire(anyString())).thenReturn(10L);

        RateLimitStatus status = rateLimitingService.getRateLimitStatus("user-1");

        // allowed = currentCount < maxRequests → 5 < 5 = false: no more requests available
        assertThat(status.allowed()).isFalse();
    }

    @Test
    @DisplayName("getRateLimitStatus handles Redis error gracefully")
    void getRateLimitStatus_redisUnavailable_returnsDefaults() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));

        RateLimitStatus status = rateLimitingService.getRateLimitStatus("user-1");

        assertThat(status.currentCount()).isEqualTo(0);
        assertThat(status.allowed()).isTrue();
    }
}
