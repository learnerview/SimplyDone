package com.learnerview.SimplyDone.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            databaseUrl = environment.getProperty("spring.datasource.url");
        }

        String jdbcUrl = normalizeJdbcUrl(databaseUrl);
        if (jdbcUrl == null) {
            return;
        }

        System.setProperty("spring.datasource.url", jdbcUrl);
        Map<String, Object> normalized = new HashMap<>();
        normalized.put("spring.datasource.url", jdbcUrl);
        environment.getPropertySources()
            .addFirst(new MapPropertySource("normalizedDatabaseUrl", normalized));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private static String normalizeJdbcUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith("jdbc:")) {
            return url;
        }
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + url.substring(url.indexOf("://") + 3);
        }
        return null;
    }
}
