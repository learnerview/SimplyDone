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
import java.util.concurrent.TimeUnit;

/**
 * Strategy for executing API call jobs.
 * Handles HTTP requests to external services with retry logic and timeout handling.
 * Uses modern RestClient for better performance and API design.
 */
@Component
@Slf4j
public class ApiCallJobStrategy implements JobExecutionStrategy {

    private final RestClient restClient;

    public ApiCallJobStrategy() {
        // Configure timeouts
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5 seconds
        factory.setReadTimeout(10000);   // 10 seconds

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
        Integer maxRetries = (Integer) params.getOrDefault("maxRetries", 3);
        
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt <= maxRetries) {
            try {
                if (attempt > 0) {
                    long backoff = (long) Math.pow(2, attempt) * 1000;
                    log.info("Retrying API call for job {} (Attempt {}/{}) in {}ms", job.getId(), attempt, maxRetries, backoff);
                    TimeUnit.MILLISECONDS.sleep(backoff);
                }
                
                ResponseEntity<String> response = executeApiCall(url, method, headers, body);
                
                // Validate response
                validateResponse(response, params);
                
                // Store response if needed
                if (Boolean.TRUE.equals(params.get("storeResponse"))) {
                    storeApiResponse(job.getId(), response);
                }
                
                log.info("API call completed successfully for job: {}. Status: {}", 
                        job.getId(), response.getStatusCode());
                return; // Success
                
            } catch (Exception e) {
                lastException = e;
                log.warn("API call attempt {} failed for job {}: {}", attempt + 1, job.getId(), e.getMessage());
                attempt++;
            }
        }
        
        log.error("API call failed after {} attempts for job {}", maxRetries + 1, job.getId());
        throw new Exception("API call failed after retries: " + lastException.getMessage(), lastException);
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
