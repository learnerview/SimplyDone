package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.entity.JobEntity;
import com.learnerview.simplydone.model.JobStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface JobEntityRepository extends JpaRepository<JobEntity, String> {
    Page<JobEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<JobEntity> findByStatusOrderByCreatedAtDesc(JobStatus status, Pageable pageable);
    Page<JobEntity> findByJobTypeOrderByCreatedAtDesc(String jobType, Pageable pageable);
    List<JobEntity> findByStatus(JobStatus status);
    List<JobEntity> findTop100ByStatusAndVisibleAtBeforeOrderByVisibleAtAsc(JobStatus status, Instant before);
    List<JobEntity> findTop100ByStatusAndNextRunAtLessThanEqualOrderByNextRunAtAsc(JobStatus status, Instant now);
    long countByStatus(JobStatus status);
    List<JobEntity> findTop20ByOrderByCreatedAtDesc();
    Optional<JobEntity> findByProducerAndIdempotencyKey(String producer, String idempotencyKey);

    @Modifying
    @Transactional
    @Query("UPDATE JobEntity j SET j.status = :runningStatus, j.leaseToken = :leaseToken, " +
           "j.leaseOwner = :leaseOwner, j.visibleAt = :visibleUntil, j.startedAt = :now, j.updatedAt = :now " +
           "WHERE j.id = :jobId AND j.status = :queuedStatus AND j.nextRunAt <= :now")
    int claimForExecution(@Param("jobId") String jobId,
                          @Param("leaseToken") String leaseToken,
                          @Param("leaseOwner") String leaseOwner,
                          @Param("visibleUntil") Instant visibleUntil,
                          @Param("now") Instant now,
                          @Param("queuedStatus") JobStatus queuedStatus,
                          @Param("runningStatus") JobStatus runningStatus);

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
