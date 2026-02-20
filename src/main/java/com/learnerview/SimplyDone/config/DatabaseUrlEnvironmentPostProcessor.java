package com.learnerview.SimplyDone.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Parses Render-style DATABASE_URL (postgresql://user:pass@host:port/db)
 * into Spring-compatible datasource properties (jdbc URL + separate credentials).
 * Acts as a backup to entrypoint.sh in case the shell script didn't run.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Only act if entrypoint hasn't already set SPRING_DATASOURCE_URL
        String alreadySet = System.getenv("SPRING_DATASOURCE_URL");
        if (alreadySet != null && !alreadySet.isBlank()) {
            return;
        }

        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }

        if (!databaseUrl.startsWith("postgres://") && !databaseUrl.startsWith("postgresql://")) {
            return;
        }

        try {
            // Parse: postgresql://user:pass@host:port/dbname
            URI uri = new URI(databaseUrl);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String dbName = uri.getPath();
            if (dbName != null && dbName.startsWith("/")) {
                dbName = dbName.substring(1);
            }

            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbcUrl);

            String userInfo = uri.getUserInfo();
            if (userInfo != null && userInfo.contains(":")) {
                String username = userInfo.substring(0, userInfo.indexOf(':'));
                String password = userInfo.substring(userInfo.indexOf(':') + 1);
                props.put("spring.datasource.username", username);
                props.put("spring.datasource.password", password);
            }

            environment.getPropertySources()
                .addFirst(new MapPropertySource("normalizedDatabaseUrl", props));

        } catch (Exception e) {
            System.err.println("Failed to parse DATABASE_URL: " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
