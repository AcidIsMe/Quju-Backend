package com.quju.repository;

import com.quju.entity.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, String> {
    Optional<ActivationToken> findByToken(String token);
    Optional<ActivationToken> findByUserId(String userId);
}
