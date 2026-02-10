package com.learnerview.simplydone.handler;

import lombok.Getter;
import java.util.Map;

@Getter
public class JobResult {
    private final boolean success;
    private final String message;
    private final Map<String, Object> data;

    private JobResult(boolean success, String message, Map<String, Object> data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static JobResult success(String message) {
        return new JobResult(true, message, null);
    }

    public static JobResult success(String message, Map<String, Object> data) {
        return new JobResult(true, message, data);
    }

    public static JobResult failure(String message) {
        return new JobResult(false, message, null);
    }
}
