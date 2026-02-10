package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface JobEntityRepository extends JpaRepository<JobEntity, String> {
    Page<JobEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<JobEntity> findByStatusOrderByCreatedAtDesc(JobStatus status, Pageable pageable);
    Page<JobEntity> findByJobTypeOrderByCreatedAtDesc(String jobType, Pageable pageable);
    List<JobEntity> findByStatus(JobStatus status);
    long countByStatus(JobStatus status);
    List<JobEntity> findTop20ByOrderByCreatedAtDesc();
    List<JobEntity> findByWorkflowId(String workflowId);

    // Throughput: successful completions since a given instant
    long countByStatusAndCompletedAtAfter(JobStatus status, Instant since);

    // Retry rate: jobs with at least one retry among terminal states
    long countByAttemptCountGreaterThanAndStatusIn(int minAttempts, List<JobStatus> statuses);

    // Latency: fetch recently completed jobs so service can average startedAt→completedAt
    @Query("SELECT j FROM JobEntity j WHERE j.status = :status " +
           "AND j.startedAt IS NOT NULL AND j.completedAt IS NOT NULL " +
           "AND j.completedAt > :since")
    List<JobEntity> findCompletedWithTimingsSince(@Param("status") JobStatus status,
                                                  @Param("since") Instant since);
}
