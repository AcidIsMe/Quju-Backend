package com.quju.repository;

import com.quju.entity.Activity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, String> {

    List<Activity> findByStatus(String status, Pageable pageable);

    List<Activity> findByCreatorId(String userId, Pageable pageable);

    List<Activity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<Activity> findByStatusAndCity(String status, String city, Pageable pageable);

    List<Activity> findByStatusAndFeeType(String status, String feeType, Pageable pageable);

    List<Activity> findByStatusAndActivityTypeIn(String status, List<String> types, Pageable pageable);

    @Query("SELECT a FROM Activity a WHERE a.status = 'published' " +
           "AND (:q IS NULL OR a.title LIKE %:q% OR a.description LIKE %:q% OR a.tags LIKE %:q%) " +
           "ORDER BY CASE WHEN a.title LIKE %:q% THEN 0 WHEN a.tags LIKE %:q% THEN 1 ELSE 2 END, a.createdAt DESC")
    List<Activity> search(@Param("q") String q, Pageable pageable);

    @Query("SELECT a FROM Activity a WHERE a.status = 'published' ORDER BY a.currentParticipants DESC")
    List<Activity> findRecommended(Pageable pageable);
}
