package com.quju.platform.dto.activity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ActivityCreateReq {

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    private String description;

    private List<String> tags;

    @JsonProperty("activity_type")
    private String activityType;

    @JsonProperty("cover_image_url")
    private String coverImageUrl;

    @NotNull
    @Future
    @JsonProperty("start_time")
    private LocalDateTime startTime;

    @NotNull
    @Future
    @JsonProperty("end_time")
    private LocalDateTime endTime;

    @NotNull
    @Future
    @JsonProperty("registration_deadline")
    private LocalDateTime registrationDeadline;

    @NotNull
    @Min(1)
    @JsonProperty("max_participants")
    private Integer maxParticipants;

    @JsonProperty("min_credit_score")
    private Integer minCreditScore;

    @Min(0)
    @JsonProperty("min_age")
    private Integer minAge;

    @JsonProperty("fee_type")
    private String feeType;

    @JsonProperty("fee_amount")
    private BigDecimal feeAmount;

    private String city;

    @JsonProperty("location_name")
    private String locationName;

    @JsonProperty("location_lat")
    private BigDecimal locationLat;

    @JsonProperty("location_lng")
    private BigDecimal locationLng;

    private Map<String, Object> geojson;

    @JsonProperty("is_team_activity")
    private Boolean teamActivity;

    @JsonProperty("team_id")
    private String teamId;

    private String status;
}
