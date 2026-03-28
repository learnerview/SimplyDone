package com.learnerview.simplydone.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Minimal Java SDK for submitting jobs to SimplyDone.
 * <p>
 * Usage:
 * <pre>
 *   var client = new SimplyDoneClient("http://localhost:8080", "sd_sk_test_user1");
 *   String result = client.submitJob("https://webhook.site/abc", Map.of("hello", "world"));
 * </pre>
 */
public class SimplyDoneClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public SimplyDoneClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = new ObjectMapper();
    }

    public String submitJob(String executionEndpoint, Map<String, Object> payload) throws IOException, InterruptedException {
        return submitJob("external", "NORMAL", executionEndpoint, payload);
    }

    public String submitJob(String jobType, String priority, String executionEndpoint, Map<String, Object> payload)
            throws IOException, InterruptedException {
        
        String idempotencyKey = java.util.UUID.randomUUID().toString();
        
        Map<String, Object> body = Map.of(
                "jobType", jobType,
                "idempotencyKey", idempotencyKey,
                "priority", priority,
                "execution", Map.of("type", "HTTP", "endpoint", executionEndpoint),
                "payload", payload
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/jobs"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-API-KEY", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Job submission failed: HTTP " + response.statusCode() + " — " + response.body());
        }
        return response.body();
    }
}
