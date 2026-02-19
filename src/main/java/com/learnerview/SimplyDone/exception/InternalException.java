package com.learnerview.SimplyDone.exception;

/**
 * Exception for internal server errors and system failures.
 * 
 * Wraps database errors, external service failures, and other
 * unexpected system problems. Typically results in HTTP 500 responses.
 */
public class InternalException extends ApplicationException {
    
    /**
     * Creates exception for internal error.
     */
    public InternalException(String message) {
        super("INTERNAL_ERROR", message);
    }
    
    /**
     * Creates exception with cause.
     */
    public InternalException(String message, Throwable cause) {
        super("INTERNAL_ERROR", message, cause);
    }
    
    /**
     * Creates exception with specific error code and message.
     */
    public InternalException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
