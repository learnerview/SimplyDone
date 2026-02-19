package com.learnerview.SimplyDone.exception;

/**
 * Base application exception for all domain-specific problems.
 * 
 * Extends RuntimeException to enable unchecked exception handling
 * alongside Spring's exception translation mechanisms.
 */
public class ApplicationException extends RuntimeException {
    
    private final String errorCode;
    
    /**
     * Creates exception with error code and message.
     */
    public ApplicationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Creates exception with error code, message, and cause.
     */
    public ApplicationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Returns the error code for programmatic handling.
     */
    public String getErrorCode() {
        return errorCode;
    }
}
