package com.learnerview.simplydone.demo;

import com.learnerview.simplydone.handler.JobContext;
import com.learnerview.simplydone.handler.JobHandler;
import com.learnerview.simplydone.handler.JobResult;
import org.springframework.stereotype.Component;

@Component
public class EchoJobHandler implements JobHandler {

    @Override
    public String getJobType() { return "echo"; }

    @Override
    public String getDescription() { return "Returns the submitted payload as the result"; }

    @Override
    public JobResult execute(JobContext ctx) {
        return JobResult.success("Echo: " + ctx.getPayload(), ctx.getPayload());
    }
}
