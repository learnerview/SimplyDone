package com.learnerview.simplydone.api;

import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.repository.JobEntityRepository;
import com.learnerview.simplydone.repository.QueueRepository;
import com.learnerview.simplydone.service.RateLimiterService;
import com.learnerview.simplydone.service.SseEmitterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"api", "test"})
class JobApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobEntityRepository jobRepo;

    @MockBean
    private QueueRepository queueRepo;

    @MockBean
    private SseEmitterService sseEmitterService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        jobRepo.deleteAll();
        when(queueRepo.queueSize(any())).thenReturn(0L);
        doNothing().when(rateLimiterService).checkRateLimit(anyString());
    }

    @Test
    void duplicateSubmissionReturnsExistingJobIdAndCreatesOneRecord() throws Exception {
        String body = """
                {
                  "jobType": "external",
                  "producer": "order-service",
                  "idempotencyKey": "order-123",
                  "priority": "NORMAL",
                  "payload": {"orderId": "123"},
                  "execution": {"type": "HTTP", "endpoint": "https://api.example.com/process"},
                  "timeoutSeconds": 10
                }
                """;

        String firstResponse = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        String secondResponse = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(jobRepo.count()).isEqualTo(1);
        JobEntity saved = jobRepo.findAll().get(0);
        assertThat(firstResponse).contains(saved.getId());
        assertThat(secondResponse).contains(saved.getId());

        verify(queueRepo, times(1)).enqueue(eq(saved.getId()), any(), anyLong());
    }

    @Test
    void queueFullReturns429() throws Exception {
        when(queueRepo.queueSize(any())).thenReturn(10000L);

        String body = """
                {
                  "jobType": "external",
                  "producer": "order-service",
                  "idempotencyKey": "order-456",
                  "priority": "NORMAL",
                  "payload": {"orderId": "456"},
                  "execution": {"type": "HTTP", "endpoint": "https://api.example.com/process"},
                  "timeoutSeconds": 10
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Queue is full")));
    }

    @Test
    void missingExecutionReturns400ValidationError() throws Exception {
        String body = """
                {
                  "jobType": "external",
                  "producer": "order-service",
                  "idempotencyKey": "order-789",
                  "priority": "NORMAL",
                  "payload": {"orderId": "789"}
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("execution")));
    }

                @Test
                void unsupportedExecutionTypeReturns400() throws Exception {
                                String body = """
                                                                {
                                                                        "jobType": "external",
                                                                        "producer": "order-service",
                                                                        "idempotencyKey": "order-900",
                                                                        "priority": "NORMAL",
                                                                        "payload": {"orderId": "900"},
                                                                        "execution": {"type": "KAFKA", "endpoint": "topic://orders"},
                                                                        "timeoutSeconds": 10
                                                                }
                                                                """;

                                mockMvc.perform(post("/api/jobs")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content(body))
                                                                .andExpect(status().isBadRequest())
                                                                .andExpect(jsonPath("$.message", containsString("Unsupported execution.type")));
                }
}
