package com.learnerview.simplydone.service;

import com.learnerview.simplydone.dto.JobResponse;
import com.learnerview.simplydone.dto.JobSubmissionRequest;
import com.learnerview.simplydone.dto.JobSubmissionResponse;

public interface JobSubmissionService {

    JobSubmissionResponse submit(JobSubmissionRequest req);

    JobResponse getJob(String jobId);

    void cancelJob(String jobId);
}
