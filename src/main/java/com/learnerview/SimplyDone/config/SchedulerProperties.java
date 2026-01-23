package com.learnerview.SimplyDone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the job scheduler - fully configurable.
 * 
 * This class holds all configurable parameters for the scheduler system,
 * allowing easy customization through application.properties without code changes.
 * Follows professional configuration patterns from reference projects.
 */
@Data
@Component
@ConfigurationProperties(prefix = "simplydone.scheduler")
public class SchedulerProperties {
    
    /**
     * Rate limiting configuration
     */
    private RateLimit rateLimit = new RateLimit();
    
    /**
     * Worker configuration
     */
    private Worker worker = new Worker();
    
    /**
     * Redis key configuration
     */
    private Redis redis = new Redis();
    
    /**
     * Queue configuration
     */
    private Queues queues = new Queues();
    
    /**
     * Statistics configuration
     */
    private Stats stats = new Stats();
    
    @Data
    public static class RateLimit {
        /**
         * Maximum number of requests allowed per minute per user
         */
        private int requestsPerMinute = 10;
    }
    
    @Data
    public static class Worker {
        /**
         * Interval in milliseconds between worker execution cycles
         */
        private long intervalMs = 1000;
    }
    
    @Data
    public static class Redis {
        /**
         * Prefix for all Redis keys used by the scheduler
         */
        private String keyPrefix = "simplydone";
    }
    
    @Data
    public static class Queues {
        /**
         * Redis key for high priority queue
         */
        private String high = "jobs:high";
        
        /**
         * Redis key for low priority queue
         */
        private String low = "jobs:low";
    }
    
    @Data
    public static class Stats {
        /**
         * Redis key for executed jobs counter
         */
        private String executed = "stats:executed";
        
        /**
         * Redis key for rejected jobs counter
         */
        private String rejected = "stats:rejected";
    }
}
