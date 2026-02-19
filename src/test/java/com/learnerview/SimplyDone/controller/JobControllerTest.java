package com.learnerview.SimplyDone.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.SimplyDone.dto.JobSubmissionRequest;
import com.learnerview.SimplyDone.dto.JobSubmissionResponse;
import com.learnerview.SimplyDone.dto.RateLimitStatus;
import com.learnerview.SimplyDone.exception.ResourceNotFoundException;
import com.learnerview.SimplyDone.exception.RateLimitException;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobType;
import com.learnerview.SimplyDone.service.JobService;
import com.learnerview.SimplyDone.service.RateLimitingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
@TestPropertySource(properties = {
    "simplydone.scheduler.api.enabled=true",
    "simplydone.scheduler.api.admin-endpoints=true",
    "simplydone.scheduler.api.view-endpoints=false",
    "simplydone.email.test-endpoints=false"
})
@DisplayName("JobController Integration Tests")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobService jobService;

    @MockBean
    private RateLimitingService rateLimitingService;

    private JobSubmissionRequest validRequest() {
        JobSubmissionRequest req = new JobSubmissionRequest();
        req.setMessage("Test job");
        req.setPriority(JobPriority.HIGH);
        req.setDelaySeconds(0);
        req.setUserId("user-1");
        req.setJobType(JobType.API_CALL);
        req.setParameters(Map.of("url", "https://example.com"));
        return req;
    }

    // -------------------------------------------------------
    // POST /api/jobs
    // -------------------------------------------------------

    @Test
    @DisplayName("POST /api/jobs returns 201 with job ID when request is valid")
    void submitJob_validRequest_returns201() throws Exception {
        when(rateLimitingService.isAllowed("user-1")).thenReturn(true);
        JobSubmissionResponse response = new JobSubmissionResponse(
                "job-1", "Test job", "HIGH", 0, Instant.now(), Instant.now(), "Job submitted successfully");
        when(jobService.submitJob(any())).thenReturn(response);

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("job-1"))
                .andExpect(jsonPath("$.data.status").value("Job submitted successfully"));
    }

    @Test
    @DisplayName("POST /api/jobs returns 429 when rate limit is exceeded")
    void submitJob_rateLimitExceeded_returns429() throws Exception {
        when(rateLimitingService.isAllowed("user-1")).thenReturn(false);
        RateLimitStatus status = new RateLimitStatus(10, 10, 45, false);
        when(rateLimitingService.getRateLimitStatus("user-1")).thenReturn(status);

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("POST /api/jobs returns 400 when message is blank")
    void submitJob_blankMessage_returns400() throws Exception {
        JobSubmissionRequest req = validRequest();
        req.setMessage("");

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/jobs returns 400 when userId is blank")
    void submitJob_blankUserId_returns400() throws Exception {
        JobSubmissionRequest req = validRequest();
        req.setUserId("");

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/jobs returns 400 when jobType is null")
    void submitJob_nullJobType_returns400() throws Exception {
        JobSubmissionRequest req = validRequest();
        req.setJobType(null);

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/jobs returns 400 for malformed JSON")
    void submitJob_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{bad json"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------
    // GET /api/jobs/{jobId}
    // -------------------------------------------------------

    @Test
    @DisplayName("GET /api/jobs/{jobId} returns 200 with job details")
    void getJobById_existingJob_returns200() throws Exception {
        when(jobService.getJobById("job-1")).thenReturn(
                com.learnerview.SimplyDone.model.Job.builder()
                        .id("job-1")
                        .jobType(JobType.API_CALL)
                        .userId("user-1")
                        .priority(JobPriority.HIGH)
                        .message("test")
                        .executeAt(Instant.now())
                        .build()
        );

        mockMvc.perform(get("/api/jobs/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/jobs/{jobId} returns 404 when job is not found")
    void getJobById_unknownJob_returns404() throws Exception {
        when(jobService.getJobById("unknown")).thenThrow(
                new ResourceNotFoundException("Job", "unknown"));

        mockMvc.perform(get("/api/jobs/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    // -------------------------------------------------------
    // DELETE /api/jobs/{jobId}
    // -------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/jobs/{jobId} returns 200 when job is cancelled")
    void cancelJob_existingJob_returns200() throws Exception {
        when(jobService.cancelJob("job-1")).thenReturn(true);

        mockMvc.perform(delete("/api/jobs/job-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/jobs/{jobId} returns 404 when job is not found")
    void cancelJob_unknownJob_returns404() throws Exception {
        when(jobService.cancelJob("unknown")).thenReturn(false);

        mockMvc.perform(delete("/api/jobs/unknown"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------
    // GET /api/jobs/health
    // -------------------------------------------------------

    @Test
    @DisplayName("GET /api/jobs/health returns 200 with status UP")
    void health_returns200() throws Exception {
        mockMvc.perform(get("/api/jobs/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // -------------------------------------------------------
    // Wrong HTTP method
    // -------------------------------------------------------

    @Test
    @DisplayName("GET on POST-only endpoint returns 405")
    void getOnSubmitEndpoint_returns405() throws Exception {
        mockMvc.perform(get("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed());
    }
}
