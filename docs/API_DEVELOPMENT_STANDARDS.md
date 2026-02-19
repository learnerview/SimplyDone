# API Development Standards

This document outlines the architectural requirements and coding standards for all SimplyDone API endpoints.

## Core Principles
- **Predictability**: All responses must follow the `ApiResponse<T>` structure.
- **Robustness**: Global exception handling converts all failures into standardized error models.
- **SOLID Compliance**: Clear separation between controller logic and service execution.

## Response Structure
Every JSON response must include:
- `status`: HTTP status code.
- `success`: Boolean indicator.
- `message`: Contextual notification string.
- `data`: The primary payload (or null).
- `timestamp`: Execution time in ISO-8601 format.

## Error Handling Standards
- **Never return raw exceptions**: Utilize the `GlobalExceptionHandler` to sanitize and wrap errors.
- **Field-level validation**: Use the `validationErrors` map to provide granular feedback for complex forms.
- **Retryable Errors**: Clearly indicate in the error code if an operation is suitable for client-side retry.

## Controller Guidelines
1. **Annotations**: Use `@RestController`, `@RequestMapping`, and `@RequiredArgsConstructor`.
2. **Validation**: Enforce constraints using `@Valid` on request bodies.
3. **Response Wrapping**: Always return `ResponseEntity<ApiResponse<T>>`.

## Evolution & Versioning
- Versioning is currently handled via endpoint path prefixes (e.g., `/api/v2/jobs`).
- Maintain backward compatibility through the use of Optional parameters and default behavior flags.
