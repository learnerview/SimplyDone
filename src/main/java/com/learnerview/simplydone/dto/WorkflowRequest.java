package com.learnerview.simplydone.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Request to submit a DAG workflow of dependent jobs.
 * DependencyResolver validates acyclicity via Kahn's topological sort.
 */
@Data
public class WorkflowRequest {

    @Valid
    @NotEmpty(message = "jobs list cannot be empty")
    private List<WorkflowJob> jobs;

    private String userId;
    private String priority;

    @Data
    public static class WorkflowJob {
        @NotBlank(message = "workflow job id is required")
        private String id;
        @NotBlank(message = "workflow job jobType is required")
        private String jobType;
        private Map<String, Object> payload;
        private List<String> dependsOn;
    }
}
