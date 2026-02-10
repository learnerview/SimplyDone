package com.learnerview.simplydone.repository;

import com.learnerview.simplydone.entity.JobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JobExecutionLogRepository extends JpaRepository<JobExecutionLog, Long> {
    List<JobExecutionLog> findByJobIdOrderByAttemptAsc(String jobId);
}
