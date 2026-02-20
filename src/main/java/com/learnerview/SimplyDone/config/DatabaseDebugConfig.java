package com.learnerview.SimplyDone.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Logs key datasource configuration at application startup for diagnostics.
 * Uses SLF4J at DEBUG level so the output is suppressed in production by default
 * and never reveals credentials (password is intentionally excluded).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseDebugConfig {

    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void logDatabaseInfo() {
        log.debug("Datasource URL    : {}", environment.getProperty("spring.datasource.url"));
        log.debug("Datasource user   : {}", environment.getProperty("spring.datasource.username"));
    }
}
