# SimplyDone - Refactored API Standards & Architecture

This document describes the refactored architecture for SimplyDone, implementing consistent response formats, comprehensive exception handling, and SOLID principles.

## Overview

The refactoring standardizes:
- **Response Format**: All endpoints return `ApiResponse<T>` wrapper
- **Exception Handling**: Unified exception hierarchy and centralized handler
- **Configuration**: Single unified profile with environment overrides
- **Code Organization**: One class per file, no nested classes
- **SOLID Principles**: Clear separation of concerns, loose coupling

## Response Format

### Standard API Response

All endpoints return responses wrapped in `ApiResponse<T>`:

```json
{
  "status": 200,
  "success": true,
  "message": "Operation successful",
  "data": { /* payload */ },
  "timestamp": "2026-02-19T10:30:00Z"
}
```

### Error Response

```json
{
  "status": 400,
  "success": false,
  "message": "Validation failed",
  "errorCode": "VALIDATION_ERROR",
  "validationErrors": {
    "userId": "User ID is required",
    "priority": "Priority must be HIGH or LOW"
  },
  "timestamp": "2026-02-19T10:30:00Z"
}
```

### Creating Responses

#### Success Responses

```java
// With data
ApiResponse.success(data, "User created successfully")

// Without data
ApiResponse.success("Operation successful")

// With custom HTTP status
ApiResponse.success(HttpStatus.CREATED.value(), data, "Resource created")
```

#### Error Responses

```java
// Basic error
ApiResponse.error(400, "INVALID_INPUT", "Invalid request parameters")

// Validation error
ApiResponse.validationError("Validation failed", fieldErrors)
```

## Exception Hierarchy

### Exception Classes

All exceptions extend `ApplicationException` base class:

```
ApplicationException (base)
├── ValidationException       → 400 Bad Request
├── ResourceNotFoundException → 404 Not Found
├── ConflictException         → 409 Conflict
├── RateLimitException        → 429 Too Many Requests
└── InternalException         → 500 Internal Server Error
```

### Using Exceptions

```java
// Validation error with field-level details
throw new ValidationException(
    "Invalid input",
    Map.of("email", "Invalid email format")
);

// Resource not found
throw new ResourceNotFoundException("User", userId);

// Business rule violation
throw new ConflictException("Job already in progress");

// Rate limit
throw new RateLimitException(
    "Rate limit exceeded",
    retryAfterSeconds
);

// System error
throw new InternalException("Database connection failed", cause);
```

### Exception Handler

The `GlobalExceptionHandler` automatically:
- Catches all exceptions
- Converts to appropriate HTTP status
- Returns standardized `ApiResponse`
- Logs with appropriate levels
- Includes retry information when applicable

## Controllers

### Template

```java
@RestController
@RequestMapping("/api/resource")
@RequiredArgsConstructor
@Slf4j
public class ResourceController {
    
    private final ResourceService service;
    
    @PostMapping
    public ResponseEntity<ApiResponse<ResponseDto>> create(
            @Valid @RequestBody CreateRequest request) {
        
        var result = service.create(request);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(
                HttpStatus.CREATED.value(),
                result,
                "Resource created successfully"
            ));
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ResourceDto>> getById(
            @PathVariable String id) {
        
        var resource = service.getById(id);
        if (resource == null) {
            throw new ResourceNotFoundException("Resource", id);
        }
        
        return ResponseEntity.ok(
            ApiResponse.success(resource, "Resource retrieved")
        );
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> delete(
            @PathVariable String id) {
        
        service.delete(id);
        return ResponseEntity.ok(
            ApiResponse.success("Resource deleted successfully")
        );
    }
}
```

### Guidelines

1. **Always wrap responses** in `ApiResponse<T>`
2. **Throw exceptions** instead of returning error responses
3. **Log appropriately**: Use `log.info()` for business events, `log.debug()` for details
4. **Use type hints**: Specify generic type for `ApiResponse<T>`
5. **Validate early**: Check input in controllers or validators
6. **Document endpoints**: Use Javadoc for parameters and return values

## Configuration

### Single Profile

Configuration is now unified in `application.properties`:

```properties
# Core
spring.application.name=SimplyDone
server.port=${PORT:8080}

# Database
spring.datasource.url=${DATABASE_URL:...}
spring.datasource.username=${DATABASE_USER:postgres}
spring.datasource.password=${DATABASE_PASSWORD:...}

# Redis
spring.data.redis.url=${REDIS_URL:...}

# Features
simplydone.scheduler.api.enabled=true
simplydone.scheduler.rate-limit.requests-per-minute=10
```

### Environment Variables

All sensitive values use environment variables:

```bash
# Required
DATABASE_URL=jdbc:postgresql://host:5432/simplydone
DATABASE_PASSWORD=secret
REDIS_URL=redis://host:6379

# Optional
PORT=8080
EMAIL_ENABLED=false
UPLOAD_DIR=/tmp/uploads
```

### Profile-Specific Overrides

For different environments, set environment variables:

```bash
# Development
PORT=8080
DATABASE_URL=jdbc:postgresql://localhost:5433/simplydone
REDIS_URL=redis://localhost:6380

# Production
PORT=8080
DATABASE_URL=jdbc:postgresql://prod-db.example.com:5432/simplydone
REDIS_URL=redis://prod-redis.example.com:6379
```

## SOLID Principles

### Single Responsibility

Each class has one reason to change:

- Controllers: HTTP request/response handling
- Services: Business logic
- Repositories: Data access
- DTOs: Data transfer
- Exceptions: Error handling

### Open/Closed

Extend functionality without modifying existing code:

```java
// Add new exception type
class PaymentException extends ApplicationException {
    // ...
}

// Handler automatically picks it up
@ExceptionHandler(PaymentException.class)
public ResponseEntity<ApiResponse<?>> handlePayment(...) { }
```

### Liskov Substitution

All exceptions can be used interchangeably:

```java
try {
    // Any ApplicationException subclass can be thrown
    service.process();
} catch (ApplicationException ex) {
    // Handle any application exception
    return ApiResponse.error(400, ex.getErrorCode(), ex.getMessage());
}
```

### Interface Segregation

Controllers depend on specific service interfaces:

```java
@RestController
public class JobController {
    private final JobSubmissionService submissionService; // Not JobService
    private final JobQueryService queryService;          // Not JobService
}
```

### Dependency Inversion

Classes depend on abstractions, not concrete implementations:

```java
// Good: Depends on interface
@RestController
public class JobController {
    private final JobService jobService; // Interface, not concrete class
}

// Bad: Depends on concrete class
public JobController(JobServiceImpl service) { }
```

## File Organization

```
src/main/java/com/learnerview/SimplyDone/
├── config/              # Spring configuration classes (one per file)
├── controller/          # REST controllers (one per file)
├── dto/                 # Data transfer objects (one per file)
├── entity/              # JPA entities (one per file)
├── exception/           # Exception classes (one per file)
├── model/               # Domain models (one per file)
├── repository/          # Data repositories (one per file)
├── service/             # Service interfaces
│   └── impl/            # Service implementations (one per file)
└── worker/              # Background workers (one per file)

src/main/resources/
└── application.properties  # Single unified configuration
```

### Rules

- **One class per file**: No nested classes
- **One responsibility**: Each class does one thing
- **Package by feature**: Group related classes
- **No cross-cutting concerns**: Use aspects/filters for cross-cutting logic
- **Clear naming**: Class name matches file name

## Testing

### Controller Tests

```java
@WebMvcTest(JobController.class)
class JobControllerTest {
    
    @Test
    void shouldReturnSuccessResponseOnValidSubmission() {
        // ApiResponse.success() should be verified
    }
    
    @Test
    void shouldReturnValidationErrorResponse() {
        // Check ApiResponse.validationError()
    }
    
    @Test
    void shouldThrowResourceNotFoundException() {
        // GlobalExceptionHandler converts to ApiResponse.error()
    }
}
```

### Service Tests

```java
@ExtendWith(MockitoExtension.class)
class JobServiceTest {
    
    @Test
    void shouldThrowValidationExceptionOnInvalidInput() {
        // Service throws, controller handles
    }
}
```

## Migration Guide

### From Old Format

**Before:**
```java
@PostMapping
public ResponseEntity<Map<String, Object>> create(...) {
    Map<String, Object> response = new HashMap<>();
    response.put("success", true);
    response.put("data", result);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}
```

**After:**
```java
@PostMapping
public ResponseEntity<ApiResponse<Result>> create(...) {
    var result = service.create(...);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(HttpStatus.CREATED.value(), result, "Created"));
}
```

### Exception Throwing

**Before:**
```java
if (job == null) {
    Map<String, Object> error = new HashMap<>();
    error.put("error", "Not found");
    error.put("message", "...");
    return ResponseEntity.status(404).body(error);
}
```

**After:**
```java
if (job == null) {
    throw new ResourceNotFoundException("Job", jobId);
    // GlobalExceptionHandler returns proper ApiResponse automatically
}
```

## Benefits

1. **Consistency**: All endpoints follow same response format
2. **Predictability**: Clients know exactly what structure to expect
3. **Maintainability**: Clear separation of concerns, easier to test
4. **Error Handling**: Centralized exception management
5. **Documentation**: Self-documenting API responses
6. **Scalability**: Easy to add new endpoints following patterns
7. **Debugging**: Standardized error messages and codes help tracking issues

## Common Mistakes to Avoid

1. ❌ Returning different response formats from different endpoints
2. ❌ Mixing exception handling in controllers and services
3. ❌ Not wrapping responses in `ApiResponse<T>`
4. ❌ Using Map<String, Object> instead of DTOs
5. ❌ Nested classes with shared responsibility
6. ❌ Exception handling in multiple layers
7. ❌ Hardcoding configuration values
8. ❌ Returning raw data instead of ApiResponse

## Frontend Integration

All frontend calls should:

1. Check `response.success` flag
2. Handle `response.errorCode` for specific errors
3. Display `response.message` to users
4. Access data in `response.data` for successful requests
5. Handle validation errors from `response.validationErrors`

Example JavaScript:

```javascript
async function submitJob(request) {
    const response = await fetch('/api/jobs', {
        method: 'POST',
        body: JSON.stringify(request)
    });
    
    const result = await response.json();
    
    if (!result.success) {
        if (result.validationErrors) {
            // Show field errors
            showValidationErrors(result.validationErrors);
        } else {
            // Show error message
            showError(result.message);
        }
        return;
    }
    
    // Process successful data
    processJob(result.data);
}
```

## References

- [SOLID Principles](https://en.wikipedia.org/wiki/SOLID)
- [REST API Best Practices](https://restfulapi.net/)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
