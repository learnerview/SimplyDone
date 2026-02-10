package com.learnerview.simplydone.demo;

import com.learnerview.simplydone.handler.JobContext;
import com.learnerview.simplydone.handler.JobHandler;
import com.learnerview.simplydone.handler.JobResult;
import org.springframework.stereotype.Component;

@Component
public class DelayJobHandler implements JobHandler {

    @Override
    public String getJobType() { return "delay"; }

    @Override
    public String getDescription() { return "Sleeps for a configurable duration (simulates long-running work)"; }

    @Override
    public JobResult execute(JobContext ctx) {
        int delayMs = 1000;
        Object raw = ctx.getPayload().get("delayMs");
        if (raw instanceof Number) delayMs = ((Number) raw).intValue();

        try {
            Thread.sleep(Math.min(delayMs, 30000)); // cap at 30s
            return JobResult.success("Delayed for " + delayMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return JobResult.failure("Interrupted");
        }
    }
}
