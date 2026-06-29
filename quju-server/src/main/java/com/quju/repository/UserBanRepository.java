package com.quju.repository;

import com.quju.entity.UserBan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserBanRepository extends JpaRepository<UserBan, String> {

    Optional<UserBan> findFirstByUserIdAndIsActiveTrueOrderByBannedAtDesc(String userId);

    boolean existsByUserIdAndIsActiveTrue(String userId);
}
