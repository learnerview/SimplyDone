package com.learnerview.SimplyDone.exception;

/**
 * Exception for requested resources that cannot be found.
 * 
 * Typically results in HTTP 404 Not Found responses.
 */
public class ResourceNotFoundException extends ApplicationException {
    
    /**
     * Creates exception for missing resource.
     */
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super("RESOURCE_NOT_FOUND", String.format("%s not found: %s", resourceType, resourceId));
    }

    /**
     * Creates exception with custom message.
     */
    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
}
