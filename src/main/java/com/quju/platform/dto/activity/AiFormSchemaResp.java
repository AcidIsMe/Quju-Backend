package com.quju.platform.dto.activity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiFormSchemaResp {

    private String title;
    private String description;
    private List<String> tags;

    @JsonProperty("activity_type")
    private String activityType;

    @JsonProperty("suggested_duration_minutes")
    private Integer suggestedDurationMinutes;

    @JsonProperty("suggested_max_participants")
    private Integer suggestedMaxParticipants;
}
