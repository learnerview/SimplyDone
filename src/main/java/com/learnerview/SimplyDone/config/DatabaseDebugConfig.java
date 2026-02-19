package com.learnerview.SimplyDone.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DatabaseDebugConfig {

    private final Environment environment;
    private final DataSource dataSource;

    public DatabaseDebugConfig(Environment environment, DataSource dataSource) {
        this.environment = environment;
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logDatabaseInfo() {
        System.out.println("=== Database Configuration Debug ===");
        System.out.println("DATABASE_URL env var: " + System.getenv("DATABASE_URL"));
        System.out.println("Spring datasource URL: " + environment.getProperty("spring.datasource.url"));
        System.out.println("Spring datasource username: " + environment.getProperty("spring.datasource.username"));
        System.out.println("Datasource class: " + dataSource.getClass().getName());
        System.out.println("=====================================");
    }
}
