package com.learnerview.simplydone.service.impl;

import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.model.JobStatus;
import com.learnerview.simplydone.repository.ApiKeyRepository;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.service.JobExecutorService;
import com.learnerview.simplydone.service.RetryService;
import com.learnerview.simplydone.service.SseEmitterService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Executes jobs by dispatching HTTP POST requests to the client's configured endpoint.
 * Protected by Resilience4j CircuitBreaker and Bulkhead to prevent cascade failures.
 * Attaches an HMAC-SHA256 signature header so clients can verify webhook authenticity.
 */
@Service
@Profile("worker")
@Slf4j
@RequiredArgsConstructor
public class JobExecutorServiceImpl implements JobExecutorService {

    private final JobEntityRepository jobRepo;
    private final RetryService retryService;
    private final SseEmitterService sseEmitterService;
    private final ApiKeyRepository apiKeyRepo;

    /** Computes HMAC-SHA256 hex digest of the payload using the producer's API key. */
    private String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.error("Failed to calculate HMAC", e);
            return "";
        }
    }

    @Override
    @CircuitBreaker(name = "externalHttpExecutor")
    @Bulkhead(name = "externalHttpExecutor", type = Bulkhead.Type.SEMAPHORE)
    public void execute(JobEntity job) {
        sseEmitterService.broadcast(job.getProducer(), "JOB_STARTED", Map.of(
                "id", job.getId(), "jobType", job.getJobType(), "status", "RUNNING",
                "priority", job.getPriority().name()
        ));

        long start = System.currentTimeMillis();
        try {
            String executionType = job.getExecutionType() != null ? job.getExecutionType().toUpperCase() : "HTTP";
            if (!"HTTP".equals(executionType)) {
                throw new IllegalArgumentException("Unsupported execution type: " + executionType);
            }
            if (job.getExecutionEndpoint() == null || job.getExecutionEndpoint().isBlank()) {
                throw new IllegalArgumentException("Missing execution endpoint for job " + job.getId());
            }

            int timeoutSeconds = job.getTimeoutSeconds() != null ? job.getTimeoutSeconds() : 10;
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(timeoutSeconds * 1000);
            requestFactory.setReadTimeout(timeoutSeconds * 1000);
            RestTemplate restTemplate = new RestTemplate(requestFactory);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            String payload = job.getPayload() != null ? job.getPayload() : "{}";
            
            apiKeyRepo.findFirstByProducerAndActiveTrueOrderByCreatedAtDesc(job.getProducer())
                    .ifPresent(apiKey -> {
                        String hmac = computeHmacSha256(payload, apiKey.getApiKey());
                        if (!hmac.isEmpty()) {
                            headers.set("X-SimplyDone-Signature", "sha256=" + hmac);
                        }
                    });

            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(job.getExecutionEndpoint(), request, String.class);
            long durationMs = System.currentTimeMillis() - start;

            if (response.getStatusCode().is2xxSuccessful()) {
                job.setStatus(JobStatus.SUCCESS);
                job.setResult(response.getBody());
                job.setVisibleAt(null);
                job.setLeaseOwner(null);
                job.setLeaseToken(null);
                job.setCompletedAt(Instant.now());
                jobRepo.save(job);

                retryService.logSuccess(job, response.getBody(), durationMs);
                sseEmitterService.broadcast(job.getProducer(), "JOB_COMPLETED", Map.of(
                        "id", job.getId(), "jobType", job.getJobType(), "status", "SUCCESS",
                        "result", response.getBody() != null ? response.getBody() : "",
                        "durationMs", durationMs
                ));
            } else {
                retryService.handleFailure(job, "HTTP " + response.getStatusCode().value(), durationMs);
            }
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            retryService.handleFailure(job,
                    e.getMessage() != null ? e.getMessage() : "Unknown error", durationMs);
        }
    }
}
