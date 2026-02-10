package com.learnerview.simplydone.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Handles DATABASE_URL from Render/Heroku which comes as:
 *   postgres://user:password@host:port/dbname
 *
 * Spring Boot JDBC needs:
 *   jdbc:postgresql://host:port/dbname  (with user/pass separate)
 *
 * If DATABASE_URL is not set or already starts with jdbc:, falls through to
 * Spring Boot's default DataSource auto-configuration (H2 for local dev).
 */
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        String databaseUrl = System.getenv("DATABASE_URL");

        if (databaseUrl != null && !databaseUrl.isBlank() && !databaseUrl.startsWith("jdbc:")) {
            return buildFromRenderUrl(databaseUrl);
        }

        // Fall through to default (H2 for local dev, or jdbc: URL if provided)
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    private DataSource buildFromRenderUrl(String databaseUrl) {
        try {
            // postgres://user:pass@host:port/db  ->  URI parse
            // Replace postgres:// with postgresql:// for URI parsing
            String normalized = databaseUrl;
            if (normalized.startsWith("postgres://")) {
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

            // Append SSL only for Render (production PostgreSQL requires SSL)
            // Render automatically sets RENDER=true in the environment
            String renderEnv = System.getenv("RENDER");
            if (renderEnv != null && !jdbcUrl.contains("sslmode")) {
                jdbcUrl += "?sslmode=require";
            }

            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(jdbcUrl);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName("org.postgresql.Driver");

            // Production connection pool tuning (Render free tier)
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
