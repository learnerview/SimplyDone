package com.learnerview.SimplyDone.exception;

/**
 * Exception for resource conflicts or constraint violations.
 * 
 * Typically results in HTTP 409 Conflict responses.
 * Examples: duplicate resource, invalid state transitions, business rule violations.
 */
public class ConflictException extends ApplicationException {
    
    /**
     * Creates exception for resource conflict.
     */
    public ConflictException(String message) {
        super("CONFLICT", message);
    }
    
    /**
     * Creates exception with cause.
     */
    public ConflictException(String message, Throwable cause) {
        super("CONFLICT", message, cause);
    }
}
