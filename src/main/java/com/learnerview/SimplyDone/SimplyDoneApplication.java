package com.learnerview.SimplyDone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Priority Job Scheduler with Rate Limiting.
 * 
 * This Spring Boot application provides:
 * - Job submission with priority (HIGH/LOW) and delay
 * - Redis-based job queue using sorted sets
 * - Rate limiting (10 jobs/minute per user)
 * - Scheduled job execution worker
 * - Admin statistics endpoint
 *
 * @version 1.0
 */
@SpringBootApplication
@EnableScheduling // Enable scheduling for the job worker
public class SimplyDoneApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimplyDoneApplication.class, args);
    }
}
