package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Strategy for executing API call jobs.
 * Handles HTTP requests to external services with retry logic and timeout handling.
 * Uses modern RestClient for better performance and API design.
 */
@Component
@Slf4j
public class ApiCallJobStrategy implements JobExecutionStrategy {

    private final RestClient restClient;

    private static final int CONNECT_TIMEOUT_MS = 5_000;  // 5 seconds
    private static final int READ_TIMEOUT_MS    = 10_000; // 10 seconds

    public ApiCallJobStrategy() {
        // Configure timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", "SimplyDone-JobScheduler/1.0")
                .build();
    }

    @Override
    public JobType getSupportedJobType() {
        return JobType.API_CALL;
    }

    @Override
    public void execute(Job job) throws Exception {
        log.info("Executing API call job: {} (ID: {})", job.getMessage(), job.getId());
        
        validateJob(job);
        
        Map<String, Object> params = job.getParameters();
        String url = (String) params.get("url");
        String method = (String) params.getOrDefault("method", "GET");
        Map<String, String> headers = (Map<String, String>) params.getOrDefault("headers", Collections.emptyMap());
        Object body = params.get("body");
        
        // B10 fix: removed internal retry loop — retries are handled by RetryService at the
        // framework level. Having both means a failing job retries up to
        // (internalRetries+1) × frameworkMaxRetries times instead of frameworkMaxRetries times.
        ResponseEntity<String> response = executeApiCall(url, method, headers, body);
        validateResponse(response, params);

        String rawBody = response.getBody();
        job.setExecutionResult(Map.of(
            "statusCode", response.getStatusCode().value(),
            "body", rawBody != null ? rawBody : "",
            // getAttemptCount() is 0-based (starts at 0, incremented on each retry);
            // +1 gives the human-readable attempt number (1 = first try, 2 = first retry, …)
            "attempt", job.getAttemptCount() + 1
        ));

        if (Boolean.TRUE.equals(params.get("storeResponse"))) {
            storeApiResponse(job.getId(), response);
        }
        
        log.info("API call completed successfully for job: {}. Status: {}", 
                job.getId(), response.getStatusCode());
    }
    
    @Override
    public void validateJob(Job job) throws IllegalArgumentException {
        if (job.getParameters() == null) {
            throw new IllegalArgumentException("API call job requires parameters");
        }
        
        Map<String, Object> params = job.getParameters();
        String url = (String) params.get("url");
        
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("API call 'url' is required");
        }
        
        if (!isValidUrl(url)) {
            throw new IllegalArgumentException("Invalid URL format: " + url);
        }
    }
    
    @Override
    public long estimateExecutionTime(Job job) {
        return 30;
    }
    
    private ResponseEntity<String> executeApiCall(String url, String method, 
                                               Map<String, String> headers, Object body) {
        
        RestClient.RequestBodySpec requestSpec = restClient.method(org.springframework.http.HttpMethod.valueOf(method.toUpperCase()))
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);
                
        headers.forEach(requestSpec::header);
        
        if (body != null && (method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT") || method.equalsIgnoreCase("PATCH"))) {
            requestSpec.body(body);
        }
        
        return requestSpec.retrieve().toEntity(String.class);
    }
    
    private void validateResponse(ResponseEntity<String> response, Map<String, Object> params) throws Exception {
        int expectedStatus = (Integer) params.getOrDefault("expectedStatus", 200);
        
        if (response.getStatusCode().value() != expectedStatus) {
            throw new Exception(String.format("Expected status %d but got %d", 
                    expectedStatus, response.getStatusCode().value()));
        }
        
        if (params.containsKey("expectedResponse")) {
            String expectedResponse = (String) params.get("expectedResponse");
            String actualResponse = response.getBody();
            
            if (actualResponse == null || !actualResponse.contains(expectedResponse)) {
                throw new Exception("Response does not contain expected content");
            }
        }
    }
    
    private void storeApiResponse(String jobId, ResponseEntity<String> response) {
        log.info("Storing API response for job {}: Status={}, Length={}", 
                jobId, response.getStatusCode(), 
                response.getBody() != null ? response.getBody().length() : 0);
    }
    
    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }
}
