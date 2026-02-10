package com.learnerview.simplydone.exception;

import java.util.Set;

public class HandlerNotFoundException extends RuntimeException {
    public HandlerNotFoundException(String jobType, Set<String> registered) {
        super("No handler for '" + jobType + "'. Available: " + registered);
    }
}
