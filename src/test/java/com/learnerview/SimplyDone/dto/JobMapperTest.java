package com.learnerview.SimplyDone.dto;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobStatus;
import com.learnerview.SimplyDone.model.JobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JobMapper Tests")
class JobMapperTest {

    @Test
    @DisplayName("toJobResponse maps all fields correctly for a fully-populated job")
    void toJobResponse_fullJob_mapsAllFields() {
        Job job = Job.builder()
                .id("job-123")
                .jobType(JobType.EMAIL_SEND)
                .userId("user-1")
                .priority(JobPriority.HIGH)
                .message("Send email")
                .status(JobStatus.PENDING)
                .executeAt(Instant.now())
                .build();

        JobResponse response = JobMapper.toJobResponse(job);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("job-123");
        assertThat(response.getJobType()).isEqualTo("EMAIL_SEND");
        assertThat(response.getPriority()).isEqualTo("HIGH");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("toJobResponse returns null when job is null")
    void toJobResponse_nullJob_returnsNull() {
        assertThat(JobMapper.toJobResponse(null)).isNull();
    }

    @Test
    @DisplayName("toJobResponse handles null priority without NPE")
    void toJobResponse_nullPriority_doesNotThrow() {
        Job job = Job.builder()
                .id("job-null-prio")
                .jobType(JobType.API_CALL)
                .userId("u")
                .message("m")
                .executeAt(Instant.now())
                .build();
        job.setPriority(null);

        assertThatNoException().isThrownBy(() -> JobMapper.toJobResponse(job));
        assertThat(JobMapper.toJobResponse(job).getPriority()).isNull();
    }

    @Test
    @DisplayName("toJobResponse handles null status without NPE")
    void toJobResponse_nullStatus_doesNotThrow() {
        Job job = Job.builder()
                .id("job-null-status")
                .jobType(JobType.API_CALL)
                .userId("u")
                .priority(JobPriority.LOW)
                .message("m")
                .executeAt(Instant.now())
                .build();
        job.setStatus(null);

        assertThatNoException().isThrownBy(() -> JobMapper.toJobResponse(job));
        assertThat(JobMapper.toJobResponse(job).getStatus()).isNull();
    }
}
