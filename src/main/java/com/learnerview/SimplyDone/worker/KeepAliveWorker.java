package com.learnerview.SimplyDone.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Prevents Render free-tier sleep by sending a self-ping every 5 minutes.
 *
 * Render spins down free web services after ~15 minutes of inactivity.
 * This worker fires a GET to the app's own health endpoint at a fixed
 * interval so the process never goes idle.
 *
 * Ping URL resolution order:
 *   1. {@code simplydone.keep-alive.url} property (explicit override)
 *   2. {@code RENDER_EXTERNAL_URL} environment variable (auto-injected by Render)
 *   3. {@code http://localhost:{PORT}/api/jobs/health} (local-dev fallback)
 *
 * Enable/disable via:
 *   simplydone.keep-alive.enabled=true   (default: true)
 */
@Component
@ConditionalOnProperty(name = "simplydone.keep-alive.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class KeepAliveWorker {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 10_000;

    private final RestClient restClient;
    private final String pingUrl;

    public KeepAliveWorker(
            @Value("${server.port:8080}") int port,
            @Value("${simplydone.keep-alive.url:}") String configuredUrl) {

        this.pingUrl = resolvePingUrl(configuredUrl, port);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();

        log.info("KeepAliveWorker initialised — ping target: {}", this.pingUrl);
    }

    /**
     * Fires a GET request to the health endpoint every 14 minutes.
     * Render's free tier idles after 15 minutes of inactivity, so pinging
     * every 14 minutes leaves a safe 60-second margin.
     * Runs 30 seconds after startup to give the application time to warm up.
     */
    @Scheduled(initialDelay = 30_000, fixedRate = 840_000)
    public void ping() {
        try {
            restClient.get().uri(pingUrl).retrieve().toBodilessEntity();
            log.debug("Keep-alive ping OK → {}", pingUrl);
        } catch (Exception e) {
            log.warn("Keep-alive ping failed → {}: {}", pingUrl, e.getMessage());
        }
    }

    /**
     * Resolves the URL to ping in priority order:
     * <ol>
     *   <li>Explicit {@code simplydone.keep-alive.url} property</li>
     *   <li>{@code RENDER_EXTERNAL_URL} environment variable (set automatically by Render)</li>
     *   <li>Localhost fallback for local development</li>
     * </ol>
     */
    static String resolvePingUrl(String configuredUrl, int port) {
        if (configuredUrl != null && !configuredUrl.isBlank()) {
            return configuredUrl;
        }
        String renderUrl = System.getenv("RENDER_EXTERNAL_URL");
        if (renderUrl != null && !renderUrl.isBlank()) {
            return renderUrl.replaceAll("/+$", "") + "/api/jobs/health";
        }
        return "http://localhost:" + port + "/api/jobs/health";
    }
}
