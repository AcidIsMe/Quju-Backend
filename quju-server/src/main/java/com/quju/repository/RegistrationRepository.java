package com.quju.repository;

import com.quju.entity.Registration;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, String> {

    Optional<Registration> findByActivityIdAndUserId(String activityId, String userId);

    boolean existsByActivityIdAndUserIdAndStatus(String activityId, String userId, String status);

    List<Registration> findByActivityIdAndStatus(String activityId, String status, Pageable pageable);

    List<Registration> findByUserIdAndStatus(String userId, String status, Pageable pageable);

    long countByActivityIdAndStatus(String activityId, String status);
}
