package com.learnerview.simplydone.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnerview.simplydone.dto.JobResponse;
import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.model.JobPriority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class JobMapper {

    private final ObjectMapper objectMapper;

    public JobResponse toResponse(JobEntity job) {
        Map<String, Object> payload = deserializePayload(job.getPayload());
        return JobResponse.builder()
                .id(job.getId())
                .jobType(job.getJobType())
                .status(job.getStatus().name())
                .priority(job.getPriority().name())
                .payload(payload)
                .result(job.getResult())
                .userId(job.getUserId())
                .scheduledAt(job.getScheduledAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .attemptCount(job.getAttemptCount())
                .maxRetries(job.getMaxRetries())
                .workflowId(job.getWorkflowId())
                .dependsOn(job.getDependsOn())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> deserializePayload(String payload) {
        if (payload == null) return Map.of();
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize payload: {}", e.getMessage());
            return Map.of();
        }
    }

    public String serializePayload(Map<String, Object> payload) {
        if (payload == null) return null;
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid payload: " + e.getMessage());
        }
    }

    public JobPriority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) return JobPriority.NORMAL;
        try {
            return JobPriority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return JobPriority.NORMAL;
        }
    }
}
