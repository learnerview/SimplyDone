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
 */
public class SimplyDoneClient {

    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public SimplyDoneClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String submitExternalJob(String producer,
                                    String idempotencyKey,
                                    String endpoint,
                                    Map<String, Object> payload) throws IOException, InterruptedException {
        Map<String, Object> request = Map.of(
                "jobType", "external",
                "producer", producer,
                "idempotencyKey", idempotencyKey,
                "priority", "NORMAL",
                "payload", payload,
                "execution", Map.of(
                        "type", "HTTP",
                        "endpoint", endpoint
                ),
                "timeoutSeconds", 10
        );

        String body = objectMapper.writeValueAsString(request);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/jobs"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to submit job: HTTP " + response.statusCode() + " - " + response.body());
        }
        return response.body();
    }
}
