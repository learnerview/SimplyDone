package com.learnerview.simplydone.exception;

public class CyclicDependencyException extends RuntimeException {
    public CyclicDependencyException(String workflowDetail) {
        super("Cyclic dependency detected in workflow: " + workflowDetail);
    }
}
