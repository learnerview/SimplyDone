package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("NotificationJobStrategy Tests")
class NotificationJobStrategyTest {

    private NotificationJobStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new NotificationJobStrategy();
    }

    private Job buildJob(Map<String, Object> params) {
        return Job.builder()
                .id("j-1")
                .jobType(JobType.NOTIFICATION)
                .userId("user-1")
                .priority(JobPriority.LOW)
                .message("notify")
                .executeAt(Instant.now())
                .parameters(params)
                .build();
    }

    @Test
    @DisplayName("getSupportedJobType returns NOTIFICATION")
    void getSupportedJobType_returnsNotification() {
        assertThat(strategy.getSupportedJobType()).isEqualTo(JobType.NOTIFICATION);
    }

    @Test
    @DisplayName("validateJob passes with all required fields")
    void validateJob_validParams_doesNotThrow() {
        Job job = buildJob(Map.of(
                "channel", "WEBHOOK",
                "webhookUrl", "https://example.com/hook",
                "message", "Hello"
        ));

        assertThatNoException().isThrownBy(() -> strategy.validateJob(job));
    }

    @Test
    @DisplayName("validateJob throws when parameters are null")
    void validateJob_nullParameters_throws() {
        assertThatThrownBy(() -> strategy.validateJob(buildJob(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws when channel is missing")
    void validateJob_missingChannel_throws() {
        Job job = buildJob(Map.of(
                "webhookUrl", "https://example.com/hook",
                "message", "Hello"
        ));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws when webhookUrl is missing")
    void validateJob_missingWebhookUrl_throws() {
        Job job = buildJob(Map.of(
                "channel", "SLACK",
                "message", "Hello"
        ));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws when webhookUrl does not start with http")
    void validateJob_invalidWebhookUrl_throws() {
        Job job = buildJob(Map.of(
                "channel", "SLACK",
                "webhookUrl", "slack://T00/B00/xxx",
                "message", "Hello"
        ));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws when message is missing")
    void validateJob_missingMessage_throws() {
        Job job = buildJob(Map.of(
                "channel", "DISCORD",
                "webhookUrl", "https://discord.com/api/webhooks/123/abc"
        ));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
