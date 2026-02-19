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
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null) {
            return;
        }

        if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
            String jdbcUrl = "jdbc:postgresql://" + databaseUrl.substring(databaseUrl.indexOf("://") + 3);
            Map<String, Object> normalized = new HashMap<>();
            normalized.put("spring.datasource.url", jdbcUrl);
            environment.getPropertySources()
                .addFirst(new MapPropertySource("normalizedDatabaseUrl", normalized));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
