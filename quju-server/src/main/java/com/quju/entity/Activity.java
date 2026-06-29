package com.quju.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "activities")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Activity {
    @Id @Column(length = 36)
    private String id;

    @Column(name = "creator_id", length = 36, nullable = false)
    private String creatorId;

    @Column(length = 100, nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(columnDefinition = "JSON")
    private String tags;

    @Column(name = "activity_type", length = 50)
    private String activityType;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "registration_deadline", nullable = false)
    private Instant registrationDeadline;

    @Column(name = "max_participants", nullable = false)
    private Integer maxParticipants;

    @Column(name = "current_participants")
    @Builder.Default
    private Integer currentParticipants = 0;

    @Column(name = "min_credit_score")
    @Builder.Default
    private Integer minCreditScore = 0;

    @Column(name = "min_age")
    @Builder.Default
    private Integer minAge = 0;

    @Column(name = "fee_type", length = 10, nullable = false)
    @Builder.Default
    private String feeType = "free";

    @Column(name = "fee_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(length = 50)
    private String city;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "location_lat", precision = 10, scale = 7)
    private BigDecimal locationLat;

    @Column(name = "location_lng", precision = 10, scale = 7)
    private BigDecimal locationLng;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "draft";

    @Column(name = "ai_review_result", length = 50)
    private String aiReviewResult;

    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;

    @Column(name = "reviewed_by", length = 36)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "is_team_activity")
    @Builder.Default
    private Boolean isTeamActivity = false;

    @Column(name = "team_id", length = 36)
    private String teamId;

    @Column(name = "cloned_from_id", length = 36)
    private String clonedFromId;

    @Column(name = "check_in_qr_code", length = 500)
    private String checkInQrCode;

    @Column(name = "check_in_enabled")
    @Builder.Default
    private Boolean checkInEnabled = false;

    @Column(name = "check_in_location_required")
    @Builder.Default
    private Boolean checkInLocationRequired = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) this.id = java.util.UUID.randomUUID().toString();
    }
}
