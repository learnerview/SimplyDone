package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ApiCallJobStrategy Tests")
class ApiCallJobStrategyTest {

    private ApiCallJobStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ApiCallJobStrategy();
    }

    private Job buildJob(Map<String, Object> params) {
        return Job.builder()
                .id("j-1")
                .jobType(JobType.API_CALL)
                .userId("user-1")
                .priority(JobPriority.HIGH)
                .message("API call job")
                .executeAt(Instant.now())
                .parameters(params)
                .build();
    }

    @Test
    @DisplayName("getSupportedJobType returns API_CALL")
    void getSupportedJobType_returnsApiCall() {
        assertThat(strategy.getSupportedJobType()).isEqualTo(JobType.API_CALL);
    }

    @Test
    @DisplayName("validateJob passes with valid URL")
    void validateJob_validUrl_doesNotThrow() {
        Job job = buildJob(Map.of("url", "https://httpbin.org/get"));

        assertThatNoException().isThrownBy(() -> strategy.validateJob(job));
    }

    @Test
    @DisplayName("validateJob throws when parameters are null")
    void validateJob_nullParameters_throwsIllegalArgument() {
        Job job = buildJob(null);

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob throws when URL is missing")
    void validateJob_missingUrl_throwsIllegalArgument() {
        Job job = buildJob(Map.of("method", "GET"));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    @DisplayName("validateJob throws when URL does not start with http")
    void validateJob_invalidUrlScheme_throwsIllegalArgument() {
        Job job = buildJob(Map.of("url", "ftp://example.com"));

        assertThatThrownBy(() -> strategy.validateJob(job))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateJob accepts http:// URLs")
    void validateJob_httpUrl_doesNotThrow() {
        Job job = buildJob(Map.of("url", "http://internal-service/health"));

        assertThatNoException().isThrownBy(() -> strategy.validateJob(job));
    }
}
