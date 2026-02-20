# API Development Standards

This document defines the conventions that all SimplyDone REST endpoints must follow. Refer to this guide when adding new endpoints or modifying existing ones.

---

## Response Envelope

Every endpoint — success or failure — must return a `ResponseEntity<ApiResponse<T>>`. The `ApiResponse<T>` class is in `com.learnerview.SimplyDone.dto.ApiResponse` and provides static factory methods:

```java
// Success with data (HTTP 200)
ApiResponse.success(data, "Human-readable message")

// Success with data and a specific HTTP status (HTTP 201)
ApiResponse.success(HttpStatus.CREATED.value(), data, "Resource created")

// Success with no data (HTTP 200)
ApiResponse.success("Operation completed")

// Error
ApiResponse.error(400, "VALIDATION_ERROR", "Input failed validation")

// Validation error with field-level detail
ApiResponse.validationError("Validation failed", Map.of("userId", "cannot be blank"))
```

The `ApiResponse<T>` fields are:

| Field | Type | Description |
|---|---|---|
| `status` | int | HTTP status code mirrored from the response |
| `success` | boolean | `true` for 2xx responses |
| `message` | string | Human-readable description of the outcome |
| `data` | T | The primary payload (null for simple success or error responses) |
| `errorCode` | string | Machine-readable error identifier (e.g., `NOT_FOUND`, `RATE_LIMIT_EXCEEDED`) |
| `validationErrors` | map | Field name to error message for `VALIDATION_ERROR` responses |
| `path` | string | The request URI, populated automatically |
| `timestamp` | Instant | When the response was generated |
| `details` | string | Additional diagnostic context (populated in non-production environments) |

---

## Controller Conventions

Every REST controller must use these annotations and patterns:

```java
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
@Slf4j
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping
    public ResponseEntity<ApiResponse<ResponseDto>> create(
            @Valid @RequestBody CreateRequest request) {
        var result = resourceService.create(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(HttpStatus.CREATED.value(), result, "Resource created"));
    }
}
```

Rules:
1. Use `@Valid` on every `@RequestBody` parameter.
2. Return `ResponseEntity<ApiResponse<T>>` — never return raw POJOs.
3. Never catch and swallow exceptions in controllers. Let `GlobalExceptionHandler` handle them.
4. Use `@Slf4j` and log at `INFO` for significant events (job submitted, job executed), `DEBUG` for internal state.

---

## Exception Handling

All exceptions are handled centrally by `GlobalExceptionHandler` in `com.learnerview.SimplyDone.exception`. Never add `try/catch` in controllers unless you need strategy-specific recovery logic.

| Exception Class | HTTP Status | Error Code |
|---|---|---|
| `ResourceNotFoundException` | 404 | `NOT_FOUND` |
| `RateLimitException` | 429 | `RATE_LIMIT_EXCEEDED` |
| `ValidationException` | 400 | `VALIDATION_ERROR` |
| `ConflictException` | 409 | `CONFLICT` |
| `InternalException` | 500 | `INTERNAL_ERROR` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `HttpRequestMethodNotSupportedException` | 405 | `METHOD_NOT_ALLOWED` |

---

## Validation

Use Jakarta Bean Validation annotations on all DTO request classes:

```java
public class JobSubmissionRequest {
    @NotBlank(message = "User ID cannot be blank")
    private String userId;

    @NotBlank(message = "Job message cannot be blank")
    private String message;

    @NotNull(message = "Job type is required")
    private JobType jobType;

    // priority is optional — service defaults null to LOW
    private JobPriority priority;
}
```

For complex cross-field validation (e.g., `senderPassword` required when `senderEmail` is set), perform validation in the service layer and throw `ValidationException` with the relevant field message.

---

## Service Layer Conventions

Services must not contain HTTP-level concerns (no `HttpServletRequest`, no status codes). Services return domain objects or DTOs. They throw domain exceptions from the `exception` package.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class JobServiceImpl implements JobService {

    @Override
    public JobSubmissionResponse submitJob(JobSubmissionRequest request) {
        // ... domain logic
        return JobMapper.toSubmissionResponse(job);
    }
}
```

---

## API Versioning

There is no URL path versioning in the current implementation. The API is at `/api/*` without a version segment.

When a breaking change is required, introduce an additive approach first: add optional fields with defaults. If a true breaking change is unavoidable, add a new endpoint path (e.g., `/api/jobs/v2`) while keeping the existing endpoint functional for backward compatibility. Do not remove existing endpoints without a deprecation notice.

---

## Logging Standards

| Level | Use |
|---|---|
| `ERROR` | Unrecoverable failures with job execution; exceptions caught in GlobalExceptionHandler at 5xx level |
| `WARN` | Rate limit exceeded; Redis unavailable (fail-open path); retries |
| `INFO` | Job submitted, job executed, job failed, job moved to DLQ |
| `DEBUG` | Redis key operations, strategy parameter detail, queue poll results |

Never log sensitive data (passwords, email credentials, PII) at any level.

