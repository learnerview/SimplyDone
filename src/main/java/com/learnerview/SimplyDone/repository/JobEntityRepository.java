package com.learnerview.SimplyDone.repository;

import com.learnerview.SimplyDone.entity.JobEntity;
import com.learnerview.SimplyDone.model.JobPriority;
import com.learnerview.SimplyDone.model.JobStatus;
import com.learnerview.SimplyDone.model.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * JPA repository for JobEntity operations.
 * Provides database persistence for job management.
 */
@Repository
public interface JobEntityRepository extends JpaRepository<JobEntity, String> {
    
    /**
     * Find jobs by user ID.
     */
    List<JobEntity> findByUserId(String userId);
    
    /**
     * Find jobs by user ID with pagination.
     */
    Page<JobEntity> findByUserId(String userId, Pageable pageable);
    
    /**
     * Find jobs by status.
     */
    List<JobEntity> findByStatus(JobStatus status);

    /**
     * Find jobs by multiple statuses.
     */
    List<JobEntity> findByStatusIn(List<JobStatus> statuses);
    
    /**
     * Find jobs by priority and status.
     */
    List<JobEntity> findByPriorityAndStatus(JobPriority priority, JobStatus status);
    
    /**
     * Find jobs by job type.
     */
    List<JobEntity> findByJobType(JobType jobType);
    
    /**
     * Find jobs submitted within a time range.
     */
    @Query("SELECT j FROM JobEntity j WHERE j.submittedAt BETWEEN :startTime AND :endTime")
    List<JobEntity> findJobsSubmittedBetween(@Param("startTime") Instant startTime, 
                                             @Param("endTime") Instant endTime);
    
    /**
     * Find jobs executed within a time range.
     */
    @Query("SELECT j FROM JobEntity j WHERE j.executedAt BETWEEN :startTime AND :endTime")
    List<JobEntity> findJobsExecutedBetween(@Param("startTime") Instant startTime, 
                                           @Param("endTime") Instant endTime);
    
    /**
     * Count jobs by status.
     */
    @Query("SELECT j.status, COUNT(j) FROM JobEntity j GROUP BY j.status")
    List<Object[]> countJobsByStatus();
    
    /**
     * Count jobs by user ID.
     */
    long countByUserId(String userId);
    
    /**
     * Find failed jobs with error messages.
     */
    @Query("SELECT j FROM JobEntity j WHERE j.status = 'FAILED' AND j.errorMessage IS NOT NULL")
    List<JobEntity> findFailedJobsWithErrors();
    
    /**
     * Get job statistics for a user.
     */
    @Query("SELECT j.jobType, j.status, COUNT(j) FROM JobEntity j WHERE j.userId = :userId " +
           "GROUP BY j.jobType, j.status")
    List<Object[]> getUserJobStatistics(@Param("userId") String userId);
    
    /**
     * Find jobs that are ready for execution.
     */
    @Query("SELECT j FROM JobEntity j WHERE j.status = 'PENDING' AND j.executeAt <= :currentTime")
    List<JobEntity> findReadyJobs(@Param("currentTime") Instant currentTime);
    
    /**
     * Delete old completed jobs (cleanup).
     */
    @Query("DELETE FROM JobEntity j WHERE j.status IN ('EXECUTED', 'FAILED') " +
           "AND j.updatedAt < :cutoffTime")
    int deleteOldJobs(@Param("cutoffTime") Instant cutoffTime);
}
