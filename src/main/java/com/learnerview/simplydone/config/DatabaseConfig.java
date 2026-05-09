package com.learnerview.simplydone.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builds a DataSource from provider-specific DATABASE_URL formats.
 */
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        String databaseUrl = System.getenv("DATABASE_URL");

        if (databaseUrl != null && !databaseUrl.isBlank() && !databaseUrl.startsWith("jdbc:")) {
            return buildFromRenderUrl(databaseUrl);
        }

        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    private DataSource buildFromRenderUrl(String databaseUrl) {
        try {
            String normalized = databaseUrl;
            if (normalized.startsWith("postgres://")) {
                // Render and Heroku use postgres://, but URI parsing expects postgresql://.
                normalized = "postgresql://" + normalized.substring("postgres://".length());
            }

            URI uri = new URI(normalized);

            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String database = uri.getPath().startsWith("/") ? uri.getPath().substring(1) : uri.getPath();
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;

            String username = null;
            String password = null;
            if (uri.getUserInfo() != null) {
                String[] userInfo = uri.getUserInfo().split(":", 2);
                username = userInfo[0];
                password = userInfo.length > 1 ? userInfo[1] : "";
            }

            String renderEnv = System.getenv("RENDER");
            if (renderEnv != null && !jdbcUrl.contains("sslmode")) {
                jdbcUrl += "?sslmode=require";
            }

            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName("org.postgresql.Driver");

            ds.setMaximumPoolSize(5);
            ds.setMinimumIdle(2);
            ds.setConnectionTimeout(20000);
            ds.setIdleTimeout(300000);
            ds.setMaxLifetime(600000);

            return ds;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid DATABASE_URL: " + e.getMessage(), e);
        }
    }
}
