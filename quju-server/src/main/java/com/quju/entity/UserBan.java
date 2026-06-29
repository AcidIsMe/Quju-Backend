package com.quju.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "user_bans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserBan {
    @Id @Column(length = 36)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Column(name = "banned_by", length = 36, nullable = false)
    private String bannedBy;

    @Column(name = "banned_at", nullable = false)
    @Builder.Default
    private Instant bannedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "revoked_by", length = 36)
    private String revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) this.id = java.util.UUID.randomUUID().toString();
    }
}
