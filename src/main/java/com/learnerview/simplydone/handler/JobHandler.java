package com.learnerview.simplydone.handler;

public interface JobHandler {
    String getJobType();
    JobResult execute(JobContext context);
    default String getDescription() { return ""; }
}
