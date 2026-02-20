# Plugin Development Guide

SimplyDone can be extended with custom job types by implementing the `JobExecutionStrategy` interface. The framework auto-discovers all strategy implementations on startup via Spring's component scan.

---

## Adding a Custom Job Type

### Step 1: Define the Job Type Enum Value

Add your new type to `com.learnerview.SimplyDone.model.JobType`:

```java
public enum JobType {
    EMAIL_SEND,
    DATA_PROCESS,
    API_CALL,
    FILE_OPERATION,
    NOTIFICATION,
    REPORT_GENERATION,
    CLEANUP,
    MY_CUSTOM_TYPE   // add this
}
```

### Step 2: Implement the Strategy

Create a new `@Component` class implementing `com.learnerview.SimplyDone.service.strategy.JobExecutionStrategy`:

```java
package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MyCustomJobStrategy implements JobExecutionStrategy {

    @Override
    public JobType getSupportedJobType() {
        return JobType.MY_CUSTOM_TYPE;
    }

    @Override
    public void execute(Job job) throws Exception {
        validateJob(job);
        // your business logic here
        log.info("Custom job executed: {}", job.getId());
    }

    @Override
    public void validateJob(Job job) throws IllegalArgumentException {
        if (job.getParameters() == null) {
            throw new IllegalArgumentException("MY_CUSTOM_TYPE requires parameters");
        }
        String requiredParam = (String) job.getParameters().get("requiredParam");
        if (requiredParam == null || requiredParam.isBlank()) {
            throw new IllegalArgumentException("requiredParam is required");
        }
    }

    @Override
    public long estimateExecutionTime(Job job) {
        return 10; // estimated seconds
    }
}
```

The `JobExecutorFactory` discovers this class automatically at startup. No registration step is needed.

### Step 3: Add Frontend Support (Optional)

To expose the new job type in the web interface:
1. Add the new type to the Job Category dropdown in `src/main/resources/templates/index.html`.
2. Add a corresponding parameter section in the Quick Launch form's JavaScript that renders the right input fields when the new type is selected.
3. Create a dedicated submission page (e.g., `my-custom-type.html`) following the pattern of `email-send.html` or `api-call.html`.

### Step 4: Document the Parameters

Add an entry to `docs/JOB_TYPE_CATALOG.md` describing all required and optional parameters for the new type.

---

## The JobExecutionStrategy Interface

```java
public interface JobExecutionStrategy {
    /**
     * Returns the JobType this strategy handles.
     * Used by JobExecutorFactory for auto-registration.
     */
    JobType getSupportedJobType();

    /**
     * Executes the job. Called by JobWorker on the background thread.
     * Throw an Exception to trigger the retry mechanism.
     */
    void execute(Job job) throws Exception;

    /**
     * Validates the job parameters before execution.
     * Throw IllegalArgumentException with a descriptive message on failure.
     */
    void validateJob(Job job) throws IllegalArgumentException;

    /**
     * Returns an estimated execution time in seconds.
     * Used for monitoring and timeout configuration.
     */
    default long estimateExecutionTime(Job job) {
        return 30;
    }
}
```

---

## Testing a Custom Strategy

Follow the pattern in the existing strategy tests (e.g., `ApiCallJobStrategyTest`, `EmailJobStrategyTest`). Use `@ExtendWith(MockitoExtension.class)` and mock any external dependencies injected into the strategy. Test the following cases at minimum:
- `getSupportedJobType()` returns the correct type
- `validateJob()` throws `IllegalArgumentException` when required parameters are missing
- `execute()` succeeds with valid parameters
- `execute()` throws an exception when the underlying operation fails

---

## Deployment Considerations

Custom strategy classes are part of the application and are compiled with it. There is no hot-plug mechanism. To deploy a new strategy:
1. Build the application JAR: `mvn clean package -DskipTests`
2. Redeploy the JAR or Docker image.
3. Confirm the new strategy appears in the startup log under `JobExecutorFactory initialized with N strategies`.

