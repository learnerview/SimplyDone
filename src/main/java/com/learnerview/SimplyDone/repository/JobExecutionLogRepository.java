package com.learnerview.SimplyDone.repository;

import com.learnerview.SimplyDone.entity.JobExecutionLog;
import com.learnerview.SimplyDone.model.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * JPA repository for JobExecutionLog operations.
 * Provides audit trail and performance metrics.
 */
@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, String> {
    
    /**
     * Find execution logs by job ID.
     */
    List<JobExecutionLog> findByJobId(String jobId);
    
    /**
     * Find execution logs by user ID.
     */
    List<JobExecutionLog> findByUserId(String userId);
    
    /**
     * Find execution logs by status.
     */
    List<JobExecutionLog> findByStatus(ExecutionStatus status);
    
    /**
     * Find execution logs within a time range.
     */
    @Query("SELECT l FROM JobExecutionLog l WHERE l.startedAt BETWEEN :startTime AND :endTime")
    List<JobExecutionLog> findExecutionLogsBetween(@Param("startTime") Instant startTime, 
                                                   @Param("endTime") Instant endTime);
    
    /**
     * Find failed executions.
     */
    @Query("SELECT l FROM JobExecutionLog l WHERE l.status = com.learnerview.SimplyDone.model.ExecutionStatus.FAILED AND l.errorMessage IS NOT NULL")
    List<JobExecutionLog> findFailedExecutions();
    
    /**
     * Get execution statistics by job type.
     */
    @Query("SELECT l.jobType, l.status, COUNT(l), AVG(l.executionTimeMs) " +
           "FROM JobExecutionLog l GROUP BY l.jobType, l.status")
    List<Object[]> getExecutionStatisticsByJobType();
    
    /**
     * Get performance metrics for a job type.
     */
    @Query("SELECT COUNT(l), AVG(l.executionTimeMs), MIN(l.executionTimeMs), MAX(l.executionTimeMs) " +
           "FROM JobExecutionLog l WHERE l.jobType = :jobType AND l.status = com.learnerview.SimplyDone.model.ExecutionStatus.COMPLETED")
    Object[] getPerformanceMetrics(@Param("jobType") String jobType);
    
    /**
     * Find long running executions.
     */
    @Query("SELECT l FROM JobExecutionLog l WHERE l.executionTimeMs > :thresholdMs ORDER BY l.executionTimeMs DESC")
    List<JobExecutionLog> findLongRunningExecutions(@Param("thresholdMs") Long thresholdMs);
    
    /**
     * Delete old execution logs (cleanup).
     */
    @Query("DELETE FROM JobExecutionLog l WHERE l.createdAt < :cutoffTime")
    int deleteOldLogs(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Count executions by status.
     */
    long countByStatus(ExecutionStatus status);
}
