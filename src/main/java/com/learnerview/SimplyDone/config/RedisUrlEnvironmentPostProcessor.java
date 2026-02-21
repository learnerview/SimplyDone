package com.learnerview.SimplyDone.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses Render-style REDIS_URL (redis://[:password@]host:port or rediss://...)
 * into individual Spring Data Redis properties.
 *
 * Mirrors DatabaseUrlEnvironmentPostProcessor for Redis/Valkey connectivity.
 * Acts as a Java-side backup to entrypoint.sh in case the shell script didn't run
 * (e.g. when running the JAR directly outside Docker).
 *
 * Supported URL formats:
 *   redis://host:port
 *   redis://:password@host:port
 *   redis://default:password@host:port
 *   rediss://... (TLS — sets spring.data.redis.ssl.enabled=true)
 */
public class RedisUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Only act if entrypoint.sh hasn't already exported SPRING_DATA_REDIS_HOST
        String alreadySet = System.getenv("SPRING_DATA_REDIS_HOST");
        if (alreadySet != null && !alreadySet.isBlank()) {
            return;
        }

        String redisUrl = System.getenv("REDIS_URL");
        if (redisUrl == null || redisUrl.isBlank()) {
            return;
        }

        if (!redisUrl.startsWith("redis://") && !redisUrl.startsWith("rediss://")) {
            return;
        }

        try {
            boolean ssl = redisUrl.startsWith("rediss://");

            // java.net.URI doesn't understand the "rediss" scheme; normalise to "redis" for parsing
            URI uri = new URI(ssl ? "redis" + redisUrl.substring(6) : redisUrl);

            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 6379;

            Map<String, Object> props = new HashMap<>();
            props.put("spring.data.redis.host", host);
            props.put("spring.data.redis.port", port);
            props.put("spring.data.redis.ssl.enabled", ssl);

            // Extract password from userinfo, ignoring an empty or "default" username
            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String password = userInfo.contains(":")
                        ? userInfo.substring(userInfo.indexOf(':') + 1)
                        : userInfo;
                if (!password.isBlank()) {
                    props.put("spring.data.redis.password", password);
                }
            }

            environment.getPropertySources()
                    .addFirst(new MapPropertySource("normalizedRedisUrl", props));

            System.out.println("==> REDIS_URL parsed by RedisUrlEnvironmentPostProcessor:"
                    + " host=" + host + ", port=" + port + ", ssl=" + ssl);

        } catch (Exception e) {
            System.err.println("Failed to parse REDIS_URL: " + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
