package com.learnerview.SimplyDone.service;

import com.learnerview.SimplyDone.model.JobType;
import com.learnerview.SimplyDone.service.strategy.JobExecutionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory for creating job execution strategies based on job type.
 * Automatically discovers and registers all JobExecutionStrategy implementations.
 *
 * This factory supports the plugin architecture by auto-discovering strategies
 * from the Spring context. Developers can create custom job types by:
 * 1. Creating a @Component that implements JobExecutionStrategy
 * 2. Implementing getSupportedJobType() to return their custom JobType
 * 3. The strategy will be automatically registered on application startup
 */
@Component
@Slf4j
public class JobExecutorFactory {

    private final Map<JobType, JobExecutionStrategy> strategies;

    /**
     * Constructor that auto-discovers and registers all job execution strategies.
     * Uses getSupportedJobType() method for automatic registration.
     *
     * @param strategyList All JobExecutionStrategy beans found in the Spring context
     */
    @Autowired
    public JobExecutorFactory(List<JobExecutionStrategy> strategyList) {
        this.strategies = new HashMap<>();

        // Auto-register strategies using getSupportedJobType()
        for (JobExecutionStrategy strategy : strategyList) {
            JobType jobType = strategy.getSupportedJobType();

            if (strategies.containsKey(jobType)) {
                log.warn("Duplicate strategy found for job type: {}. Using: {}",
                        jobType, strategy.getClass().getSimpleName());
            }

            strategies.put(jobType, strategy);
            log.info("Registered job execution strategy: {} for type: {}",
                    strategy.getClass().getSimpleName(), jobType);
        }

        log.info("JobExecutorFactory initialized with {} strategies: {}",
                strategies.size(),
                strategies.keySet().stream()
                        .map(JobType::name)
                        .collect(Collectors.joining(", ")));
    }

    /**
     * Gets the appropriate execution strategy for the given job type.
     *
     * @param jobType The type of job to execute
     * @return The execution strategy for the job type
     * @throws IllegalArgumentException if no strategy is found for the job type
     */
    public JobExecutionStrategy getStrategy(JobType jobType) {
        JobExecutionStrategy strategy = strategies.get(jobType);
        if (strategy == null) {
            throw new IllegalArgumentException(
                    String.format("No execution strategy found for job type: %s. " +
                            "Registered types: %s",
                            jobType,
                            strategies.keySet().stream()
                                    .map(JobType::name)
                                    .collect(Collectors.joining(", "))));
        }
        return strategy;
    }

    /**
     * Checks if a strategy is available for the given job type.
     *
     * @param jobType The job type to check
     * @return true if a strategy is available, false otherwise
     */
    public boolean hasStrategy(JobType jobType) {
        return strategies.containsKey(jobType);
    }

    /**
     * Gets all supported job types.
     *
     * @return Array of supported job types
     */
    public JobType[] getSupportedJobTypes() {
        return strategies.keySet().toArray(new JobType[0]);
    }

    /**
     * Gets the total number of registered strategies.
     *
     * @return Number of registered strategies
     */
    public int getStrategyCount() {
        return strategies.size();
    }

    /**
     * Gets a map of all registered strategies.
     * Useful for admin interfaces and diagnostics.
     *
     * @return Map of JobType to strategy class names
     */
    public Map<JobType, String> getStrategyInfo() {
        return strategies.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getClass().getSimpleName()
                ));
    }
}
