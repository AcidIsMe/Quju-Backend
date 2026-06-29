package com.quju.repository;

import com.quju.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, String> {
    List<LoginAttempt> findByEmailAndCreatedAtAfter(String email, Instant after);
}
