package com.learnerview.SimplyDone.exception;

import com.learnerview.SimplyDone.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the SimplyDone application.
 * 
 * Provides consistent error responses across all REST endpoints using ApiResponse.
 * Handles validation, business logic, and system errors with appropriate HTTP status codes.
 * All exceptions are logged appropriately for monitoring and debugging.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    /**
     * Handles ApplicationException and its subclasses.
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiResponse<?>> handleApplicationException(ApplicationException ex) {
        log.warn("Application error [{}]: {}", ex.getErrorCode(), ex.getMessage());
        
        ApiResponse<?> response = ApiResponse.error(
            400,
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handles validation exceptions with field errors.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        
        ApiResponse<?> response = ApiResponse.validationError(
            ex.getMessage(),
            ex.getFieldErrors()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    /**
     * Handles resource not found exceptions.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        
        ApiResponse<?> response = ApiResponse.error(
            404,
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    /**
     * Handles conflict exceptions (409).
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleConflictException(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        
        ApiResponse<?> response = ApiResponse.error(
            409,
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    /**
     * Handles rate limit exceptions (429).
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<?>> handleRateLimitException(RateLimitException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        
        ApiResponse<?> response = ApiResponse.error(
            429,
            ex.getErrorCode(),
            ex.getMessage()
        );
        
        return ResponseEntity
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
            .body(response);
    }
    
    /**
     * Handles internal exceptions (500).
     */
    @ExceptionHandler(InternalException.class)
    public ResponseEntity<ApiResponse<?>> handleInternalException(InternalException ex) {
        log.error("Internal error [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        ApiResponse<?> response = ApiResponse.error(
            500,
            ex.getErrorCode(),
            "An internal error occurred. Please try again later."
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    /**
     * Handles validation exceptions for request body validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Validation error in request body: {}", errors);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.validationError("Request validation failed", errors));
    }
    
    /**
     * Handles validation exceptions for form data binding.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleBindException(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        log.warn("Validation error in form binding: {}", errors);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.validationError("Form validation failed", errors));
    }
    
    /**
     * Handles type mismatch exceptions (e.g., invalid enum values).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        
        String message = String.format(
            "Invalid value for parameter '%s': %s",
            ex.getName(),
            ex.getValue()
        );
        
        log.warn(message);
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(400, "INVALID_PARAMETER", message));
    }
    
    /**
     * Handles illegal argument exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(
            IllegalArgumentException ex) {
        
        log.warn("Illegal argument: {}", ex.getMessage());
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(400, "INVALID_ARGUMENT", ex.getMessage()));
    }
    
    /**
     * Handles JSON parsing errors.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        
        log.warn("Malformed JSON: {}", ex.getMessage());
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(
                400,
                "MALFORMED_JSON",
                "Request body could not be parsed. Please check your JSON format."
            ));
    }
    
    /**
     * Handles unsupported HTTP methods.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        
        log.warn("HTTP method not supported: {}", ex.getMethod());
        
        return ResponseEntity
            .status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ApiResponse.error(
                405,
                "METHOD_NOT_ALLOWED",
                String.format("HTTP method '%s' is not supported for this endpoint", ex.getMethod())
            ));
    }
    
    /**
     * Handles 404 errors for non-existent endpoints.
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoHandlerFound(
            NoHandlerFoundException ex) {
        
        log.warn("Endpoint not found: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(
                404,
                "ENDPOINT_NOT_FOUND",
                String.format("Endpoint '%s' does not exist", ex.getRequestURL())
            ));
    }
    
    /**
     * Handles runtime exceptions (catch-all for unexpected errors).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<?>> handleRuntimeException(RuntimeException ex) {
        log.error("Unexpected runtime exception", ex);
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                500,
                "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later."
            ));
    }
    
    /**
     * Catch-all handler for any other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(Exception ex) {
        log.error("Unexpected exception", ex);
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(
                500,
                "INTERNAL_ERROR",
                "An internal error occurred. Please try again later."
            ));
    }
}
