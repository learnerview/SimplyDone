package com.learnerview.SimplyDone.repository;

import com.learnerview.SimplyDone.entity.UserActivity;
import com.learnerview.SimplyDone.model.ActivityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * JPA repository for UserActivity operations.
 * Provides analytics and rate limiting support.
 */
@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, String> {
    
    /**
     * Find activities by user ID.
     */
    List<UserActivity> findByUserId(String userId);
    
    /**
     * Find activities by user ID with pagination.
     */
    Page<UserActivity> findByUserId(String userId, Pageable pageable);
    
    /**
     * Find activities by type.
     */
    List<UserActivity> findByActivityType(ActivityType activityType);
    
    /**
     * Find activities within a time range.
     */
    @Query("SELECT a FROM UserActivity a WHERE a.timestamp BETWEEN :startTime AND :endTime")
    List<UserActivity> findActivitiesBetween(@Param("startTime") Instant startTime, 
                                             @Param("endTime") Instant endTime);
    
    /**
     * Count activities by user in time window (for rate limiting).
     */
    @Query("SELECT COUNT(a) FROM UserActivity a WHERE a.userId = :userId " +
           "AND a.activityType = :activityType AND a.timestamp >= :since")
    long countUserActivitiesInWindow(@Param("userId") String userId,
                                     @Param("activityType") ActivityType activityType,
                                     @Param("since") Instant since);
    
    /**
     * Get user activity statistics.
     */
    @Query("SELECT a.activityType, COUNT(a) FROM UserActivity a WHERE a.userId = :userId " +
           "GROUP BY a.activityType")
    List<Object[]> getUserActivityStatistics(@Param("userId") String userId);
    
    /**
     * Get failed activities.
     */
    @Query("SELECT a FROM UserActivity a WHERE a.success = false AND a.errorMessage IS NOT NULL")
    List<UserActivity> findFailedActivities();
    
    /**
     * Count activities by type in time range.
     */
    @Query("SELECT COUNT(a) FROM UserActivity a WHERE a.activityType = :activityType " +
           "AND a.timestamp BETWEEN :startTime AND :endTime")
    long countActivitiesByTypeInTimeRange(@Param("activityType") ActivityType activityType,
                                          @Param("startTime") Instant startTime,
                                          @Param("endTime") Instant endTime);
    
    /**
     * Delete old activity logs (cleanup).
     */
    @Query("DELETE FROM UserActivity a WHERE a.createdAt < :cutoffTime")
    int deleteOldActivities(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Find activities by IP address (security monitoring).
     */
    List<UserActivity> findByIpAddress(String ipAddress);
    
    /**
     * Get most active users.
     */
    @Query("SELECT a.userId, COUNT(a) as activityCount FROM UserActivity a " +
           "GROUP BY a.userId ORDER BY activityCount DESC")
    List<Object[]> getMostActiveUsers();
}
