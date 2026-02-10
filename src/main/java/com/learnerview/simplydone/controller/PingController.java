package com.learnerview.simplydone.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Ultra-lightweight ping endpoint.
 * No DB, no Redis — responds instantly.
 *
 * Used for:
 *  1. Browser-initiated keep-alive: JS pings every 4 min to prevent Render free tier sleep.
 *  2. External uptime monitors (UptimeRobot, cron-job.org) — pin this URL every 14 min.
 *
 * Render free tier sleeps after 15 min of inactivity.
 * An internal @Scheduled ping WON'T work — the JVM is suspended when sleeping.
 * The browser ping (client-driven) and an external cron are the only reliable solutions.
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "pong", true,
                "ts", Instant.now().toString()
        ));
    }
}
