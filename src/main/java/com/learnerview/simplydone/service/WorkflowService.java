package com.learnerview.simplydone.service;

import com.learnerview.simplydone.dto.JobSubmissionResponse;
import com.learnerview.simplydone.dto.WorkflowRequest;
import com.learnerview.simplydone.entity.JobEntity;

import java.util.List;

public interface WorkflowService {

    List<JobSubmissionResponse> submitWorkflow(WorkflowRequest req);

    void unblockDependents(JobEntity completedJob);
}
