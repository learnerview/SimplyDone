package com.learnerview.SimplyDone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Configuration properties for the job scheduler - fully configurable.
 *
 * This class holds all configurable parameters for the scheduler system,
 * allowing easy customization through application.properties without code changes.
 * Follows professional configuration patterns from reference projects.
 */
@Component
@ConfigurationProperties(prefix = "simplydone.scheduler")
@Validated
public class SchedulerProperties {

    private Api api = new Api();
    private RateLimit rateLimit = new RateLimit();
    private Worker worker = new Worker();
    private Redis redis = new Redis();
    private Queues queues = new Queues();
    private Stats stats = new Stats();

    public Api getApi() { return api; }
    public void setApi(Api api) { this.api = api; }

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    public Worker getWorker() { return worker; }
    public void setWorker(Worker worker) { this.worker = worker; }

    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }

    public Queues getQueues() { return queues; }
    public void setQueues(Queues queues) { this.queues = queues; }

    public Stats getStats() { return stats; }
    public void setStats(Stats stats) { this.stats = stats; }

    /**
     * API configuration for enabling/disabling REST endpoints
     */
    public static class Api {
        private boolean enabled = true;
        private boolean adminEndpoints = true;
        private boolean viewEndpoints = false; // Thymeleaf views disabled by default

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public boolean isAdminEndpoints() { return adminEndpoints; }
        public void setAdminEndpoints(boolean adminEndpoints) { this.adminEndpoints = adminEndpoints; }

        public boolean isViewEndpoints() { return viewEndpoints; }
        public void setViewEndpoints(boolean viewEndpoints) { this.viewEndpoints = viewEndpoints; }
    }

    public static class RateLimit {
        @Min(1)
        private int requestsPerMinute = 10;
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
    }

    public static class Worker {
        @Min(100)
        private long intervalMs = 1000;
        /** Max jobs drained per polling cycle. Increase for higher throughput. */
        @Min(1)
        private int maxJobsPerCycle = 5;
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public int getMaxJobsPerCycle() { return maxJobsPerCycle; }
        public void setMaxJobsPerCycle(int maxJobsPerCycle) { this.maxJobsPerCycle = maxJobsPerCycle; }
    }

    public static class Redis {
        @NotBlank
        private String keyPrefix = "simplydone";
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    }

    public static class Queues {
        @NotBlank
        private String high = "jobs:high";
        @NotBlank
        private String low = "jobs:low";
        @NotBlank
        private String deadLetter = "dead_letter:jobs";
        public String getHigh() { return high; }
        public void setHigh(String high) { this.high = high; }
        public String getLow() { return low; }
        public void setLow(String low) { this.low = low; }
        public String getDeadLetter() { return deadLetter; }
        public void setDeadLetter(String deadLetter) { this.deadLetter = deadLetter; }
    }

    public static class Stats {
        @NotBlank
        private String executed = "stats:executed";
        @NotBlank
        private String rejected = "stats:rejected";
        public String getExecuted() { return executed; }
        public void setExecuted(String executed) { this.executed = executed; }
        public String getRejected() { return rejected; }
        public void setRejected(String rejected) { this.rejected = rejected; }
    }
}
