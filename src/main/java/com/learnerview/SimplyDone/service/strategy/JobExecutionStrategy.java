package com.learnerview.SimplyDone.service.strategy;

import com.learnerview.SimplyDone.model.Job;
import com.learnerview.SimplyDone.model.JobType;

/**
 * Service Provider Interface (SPI) for job execution strategies.
 *
 * This interface enables a plugin-based architecture where developers can:
 * 1. Implement custom job types by creating a @Component that implements this interface
 * 2. Auto-register their strategies without modifying the framework code
 * 3. Override default behavior for estimation, validation, and resource management
 *
 * Example custom job implementation:
 * <pre>
 * {@code
 * @Component
 * public class CustomJobStrategy implements JobExecutionStrategy {
 *     @Override
 *     public JobType getSupportedJobType() {
 *         return JobType.CUSTOM_JOB; // Your custom job type
 *     }
 *
 *     @Override
 *     public void execute(Job job) throws Exception {
 *         // Your custom execution logic here
 *     }
 *
 *     @Override
 *     public void validateJob(Job job) {
 *         // Your custom validation logic
 *     }
 * }
 * }
 * </pre>
 *
 * The framework automatically discovers all implementations and registers them.
 */
public interface JobExecutionStrategy {

    /**
     * Returns the job type that this strategy handles.
     * This method is used for auto-discovery and registration of strategies.
     *
     * IMPORTANT: Each strategy must return a unique JobType.
     *
     * @return The job type this strategy supports
     */
    JobType getSupportedJobType();

    /**
     * Executes a job of the supported type.
     * This method contains the core business logic for the job execution.
     *
     * @param job The job to execute
     * @throws Exception if execution fails (will trigger retry logic)
     */
    void execute(Job job) throws Exception;

    /**
     * Validates that the job has all required parameters for this execution type.
     * Called before job execution to fail fast on invalid inputs.
     *
     * @param job The job to validate
     * @throws IllegalArgumentException if validation fails
     */
    void validateJob(Job job) throws IllegalArgumentException;

    /**
     * Returns the estimated execution time in seconds for this job type.
     * Used for scheduling, timeout calculations, and capacity planning.
     *
     * @param job The job to estimate time for
     * @return Estimated execution time in seconds
     */
    default long estimateExecutionTime(Job job) {
        return 60; // Default 1 minute
    }

    /**
     * Returns the resource requirements for this job type.
     * Used for resource allocation, load balancing, and throttling decisions.
     *
     * @return Resource requirements
     */
    default ResourceRequirements getResourceRequirements() {
        return new ResourceRequirements(1, 100); // 1 CPU, 100MB memory
    }

    /**
     * Returns whether this job type supports cancellation.
     * Override to return true if your job can be safely interrupted mid-execution.
     *
     * @return true if job can be cancelled, false otherwise
     */
    default boolean isCancellable() {
        return false;
    }

    /**
     * Returns whether this job type is idempotent.
     * Idempotent jobs can be safely retried without side effects.
     *
     * @return true if job is idempotent, false otherwise
     */
    default boolean isIdempotent() {
        return true; // Most jobs should be idempotent for safe retries
    }

    /**
     * Returns a human-readable description of what this job type does.
     * Used for documentation and admin UI displays.
     *
     * @return Description of the job type
     */
    default String getDescription() {
        return "Executes " + getSupportedJobType().getDisplayName() + " jobs";
    }

    /**
     * Resource requirements for job execution.
     */
    class ResourceRequirements {
        private final int cpuUnits;
        private final long memoryMB;

        public ResourceRequirements(int cpuUnits, long memoryMB) {
            this.cpuUnits = cpuUnits;
            this.memoryMB = memoryMB;
        }

        public int getCpuUnits() { return cpuUnits; }
        public long getMemoryMB() { return memoryMB; }

        @Override
        public String toString() {
            return String.format("CPU: %d units, Memory: %d MB", cpuUnits, memoryMB);
        }
    }
}
