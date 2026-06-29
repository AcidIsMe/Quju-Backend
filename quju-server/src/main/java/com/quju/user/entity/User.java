package com.quju.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    @Column(name = "nickname", length = 50, nullable = false, unique = true)
    private String nickname;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "bio", length = 200)
    private String bio;

    @Column(name = "interest_tags", columnDefinition = "JSON")
    private String interestTags;

    @Column(name = "role", length = 20, nullable = false)
    @Builder.Default
    private String role = "personal";

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "pending_activation";

    @Column(name = "credit_score")
    @Builder.Default
    private Integer creditScore = 100;

    @Column(name = "location_lat", precision = 10, scale = 7)
    private java.math.BigDecimal locationLat;

    @Column(name = "location_lng", precision = 10, scale = 7)
    private java.math.BigDecimal locationLng;

    @Column(name = "location_updated_at")
    private Instant locationUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = java.util.UUID.randomUUID().toString();
        }
    }
}
