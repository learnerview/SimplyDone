package com.learnerview.SimplyDone.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception for validation failures in request parameters or business logic.
 * 
 * Carries field-level validation errors for detailed error reporting to clients.
 */
public class ValidationException extends ApplicationException {
    
    private final Map<String, String> fieldErrors;
    
    /**
     * Creates exception with validation errors.
     */
    public ValidationException(String message, Map<String, String> fieldErrors) {
        super("VALIDATION_ERROR", message);
        this.fieldErrors = fieldErrors != null ? fieldErrors : new HashMap<>();
    }
    
    /**
     * Creates exception with a single field error.
     */
    public ValidationException(String message, String field, String error) {
        super("VALIDATION_ERROR", message);
        this.fieldErrors = new HashMap<>();
        this.fieldErrors.put(field, error);
    }
    
    /**
     * Returns field-level validation errors.
     */
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
