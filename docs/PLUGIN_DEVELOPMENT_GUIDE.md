# Plugin Development Guide

SimplyDone can be extended by implementing custom job strategies or deploying the entire system as a microservice plugin.

## Extending Job Types
To add a new capability to the scheduler, follow the Strategy implementation pattern.

### 1. Define the Strategy
Create a new class implementing the `JobStrategy` interface:
```java
@Component
public class CustomStrategy implements JobStrategy {
    @Override
    public JobResult execute(JobRequest request) {
        // Implementation logic
    }
}
```

### 2. Register Parameters
Update the frontend form and API documentation to reflect the parameters required for the new `jobType`.

## Microservice Plugin Mode
SimplyDone can function as a dedicated queuing service for larger ecosystems.

### Integration Standards
- **Secured Communication**: All plugin-specific endpoints require an `X-Plugin-API-Key`.
- **Resource Isolation**: Use `REDIS_NAMESPACE` to prevent key collisions in shared clusters.
- **Standardized Health**: Utilize `/api/plugin/status` for readiness and liveness probes.

### External Integration Client
When integrating with a host application, utilize a dedicated `PluginClient` to handle transient network failures and ensure consistent response parsing.

## API Consistency
All plugins and extensions must adhere to the [API Development Standards](API_DEVELOPMENT_STANDARDS.md) for error handling and response wrapping.
