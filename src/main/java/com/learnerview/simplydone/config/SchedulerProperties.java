package com.learnerview.simplydone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "simplydone")
@Data
public class SchedulerProperties {

    private final Scheduler scheduler = new Scheduler();
    private final RateLimit rateLimit = new RateLimit();
    private final Retry retry = new Retry();

    @Data
    public static class Scheduler {
        private long pollingIntervalMs = 1000;
        private String queuePrefix = "simplydone:queue";
        private final Weights weights = new Weights();

        @Data
        public static class Weights {
            private int high = 70;
            private int normal = 20;
            private int low = 10;
        }
    }

    @Data
    public static class RateLimit {
        private int requestsPerMinute = 60;
        private int windowSeconds = 60;
    }

    @Data
    public static class Retry {
        private int maxAttempts = 3;
        private int initialDelaySeconds = 5;
        private double backoffMultiplier = 2.0;
    }
}
