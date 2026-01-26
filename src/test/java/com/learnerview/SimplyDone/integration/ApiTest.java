package com.learnerview.SimplyDone.integration;

import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles("test")
class ApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testJobSubmission() {
        JobSubmissionRequest request = new JobSubmissionRequest();
        request.setMessage("Test job");
        request.setPriority(com.learnerview.SimplyDone.model.JobPriority.HIGH);
        request.setDelaySeconds(5);
        request.setUserId("testuser");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JobSubmissionRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "http://localhost:" + port + "/api/jobs", entity, Map.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody().get("id"));
    }

    @Test
    void testHealth() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/admin/health", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void testActuator() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void testDeadLetterQueueEndpoint() {
        // Test dead letter queue endpoint exists
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/admin/dead-letter-queue", Map.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().get("deadLetterJobs"));
        assertNotNull(response.getBody().get("totalJobs"));
    }

    @Test
    void testDeadLetterRetryEndpoint() {
        // Test dead letter retry endpoint (should return 404 for non-existent job)
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/admin/dead-letter-queue/fake-job-id/retry", 
                null, 
                Map.class);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        } catch (Exception e) {
            // Accept either 404 status or exception containing 404
            assertTrue(e.getMessage().contains("404") || e.getMessage().contains("404"));
        }
    }
}
