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
        normalizeDatabaseUrl();
        SpringApplication.run(SimplyDoneApplication.class, args);
    }

    private static void normalizeDatabaseUrl() {
        String existing = System.getProperty("spring.datasource.url");
        String envOverride = System.getenv("SPRING_DATASOURCE_URL");
        if ((existing != null && !existing.isBlank()) || (envOverride != null && !envOverride.isBlank())) {
            return;
        }

        String envUrl = System.getenv("DATABASE_URL");
        if (envUrl == null || envUrl.isBlank()) {
            return;
        }

        String jdbcUrl = envUrl;
        if (envUrl.startsWith("postgres://")) {
            jdbcUrl = "jdbc:postgresql://" + envUrl.substring("postgres://".length());
        } else if (envUrl.startsWith("postgresql://")) {
            jdbcUrl = "jdbc:postgresql://" + envUrl.substring("postgresql://".length());
        }

        if (jdbcUrl.startsWith("jdbc:postgresql://")) {
            System.setProperty("spring.datasource.url", jdbcUrl);
        }
    }
}
