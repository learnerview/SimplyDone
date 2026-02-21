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
 * Enable/disable via:
 *   simplydone.keep-alive.enabled=true   (default: true)
 *   simplydone.keep-alive.url=...        (default: http://localhost:{PORT}/api/jobs/health)
 */
@Component
@ConditionalOnProperty(name = "simplydone.keep-alive.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class KeepAliveWorker {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 5_000;

    private final RestClient restClient;
    private final String pingUrl;

    public KeepAliveWorker(
            @Value("${server.port:8080}") int port,
            @Value("${simplydone.keep-alive.url:}") String configuredUrl) {

        this.pingUrl = configuredUrl.isEmpty()
                ? "http://localhost:" + port + "/api/jobs/health"
                : configuredUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Fires a GET request to the health endpoint every 5 minutes (300 000 ms).
     * Runs 60 seconds after startup to give the application time to warm up.
     */
    @Scheduled(initialDelay = 60_000, fixedRate = 300_000)
    public void ping() {
        try {
            restClient.get().uri(pingUrl).retrieve().toBodilessEntity();
            log.debug("Keep-alive ping OK → {}", pingUrl);
        } catch (Exception e) {
            log.warn("Keep-alive ping failed → {}: {}", pingUrl, e.getMessage());
        }
    }
}
