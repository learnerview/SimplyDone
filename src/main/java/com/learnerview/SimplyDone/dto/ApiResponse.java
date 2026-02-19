package com.learnerview.SimplyDone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

// wrapper that all API endpoints return
// makes all responses look the same - whether success or error
// the frontend checks success and reads data or message accordingly
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    private int status;      // http status code: 200, 201, 400, 404, etc.
    
    private boolean success;  // true if the request worked, false if something went wrong
    
    private String message;   // human-readable info about what happened
    
    private T data;           // the actual result (null for simple success or error responses)
    
    private Map<String, String> validationErrors;  // field -> error message, only set on 400 errors
    
    private String errorCode;  // machine-readable code for the frontend to handle e.g. NOT_FOUND, RATE_LIMIT_EXCEEDED
    
    private String path;        // the endpoint path that was called (Rule 7)
    
    private Instant timestamp;  // when this response was generated
    
    private String details;  // extra error info, shown in dev mode
    
    // helper to get the current request path
    private static String getCurrentPath() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attrs = 
                (org.springframework.web.context.request.ServletRequestAttributes) 
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest().getRequestURI() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // success response that includes data
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .status(200)
            .success(true)
            .message(message)
            .data(data)
            .path(getCurrentPath())
            .timestamp(Instant.now())
            .build();
    }
    
    // success response with data + custom http status (e.g. 201 for created)
    public static <T> ApiResponse<T> success(int httpStatus, T data, String message) {
        return ApiResponse.<T>builder()
            .status(httpStatus)
            .success(true)
            .message(message)
            .data(data)
            .path(getCurrentPath())
            .timestamp(Instant.now())
            .build();
    }
    
    // success response with just a message and no data
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
            .status(200)
            .success(true)
            .message(message)
            .path(getCurrentPath())
            .timestamp(Instant.now())
            .build();
    }
    
    // error response
    public static <T> ApiResponse<T> error(int httpStatus, String errorCode, String message) {
        return ApiResponse.<T>builder()
            .status(httpStatus)
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .path(getCurrentPath())
            .timestamp(Instant.now())
            .build();
    }
    
    // error response with extra details (stack trace etc.)
    public static <T> ApiResponse<T> error(int httpStatus, String errorCode, String message, String details) {
        return ApiResponse.<T>builder()
            .status(httpStatus)
            .success(false)
            .errorCode(errorCode)
            .message(message)
            .details(details)
            .path(getCurrentPath())
            .timestamp(Instant.now())
            .build();
    }
    
    // 400 error for when the request body fails validation
    public static <T> ApiResponse<T> validationError(String message, Map<String, String> validationErrors) {
        return ApiResponse.<T>builder()
            .status(400)
            .success(false)
            .errorCode("VALIDATION_ERROR")
            .message(message)
            .validationErrors(validationErrors)
            .path(getCurrentPath())
            .timestamp(Instant.now())
            .build();
    }
}
