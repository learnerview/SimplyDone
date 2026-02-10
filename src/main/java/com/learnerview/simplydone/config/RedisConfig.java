package com.learnerview.simplydone.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.time.Duration;

/**
 * Redis configuration that handles Render's REDIS_URL format.
 *
 * Render provides:
 *   - Free tier:  redis://red-xxx:6379            (no auth, no TLS, internal)
 *   - Paid tier:  rediss://user:pass@host:port     (TLS with auth)
 *
 * Falls back to localhost:6380 for local dev if REDIS_URL is not set.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        String redisUrl = System.getenv("REDIS_URL");

        if (redisUrl == null || redisUrl.isBlank()) {
            redisUrl = "redis://localhost:6380";
        }

        boolean useSsl = redisUrl.startsWith("rediss://");

        // Normalize rediss:// to redis:// for URI parsing
        String normalized = useSsl ? redisUrl.replaceFirst("rediss://", "redis://") : redisUrl;
        URI uri = URI.create(normalized);

        RedisStandaloneConfiguration serverConfig = new RedisStandaloneConfiguration();
        serverConfig.setHostName(uri.getHost());
        serverConfig.setPort(uri.getPort() > 0 ? uri.getPort() : 6379);

        if (uri.getUserInfo() != null) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            if (userInfo.length == 2) {
                serverConfig.setUsername(userInfo[0]);
                serverConfig.setPassword(userInfo[1]);
            } else if (userInfo.length == 1) {
                serverConfig.setPassword(userInfo[0]);
            }
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(2));

        if (useSsl) {
            clientConfigBuilder.useSsl().disablePeerVerification();
        }

        return new LettuceConnectionFactory(serverConfig, clientConfigBuilder.build());
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
