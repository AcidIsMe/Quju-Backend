package com.quju.platform.dto.social;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class SquadCreateReq {

    @NotBlank
    private String name;

    private String description;

    @JsonProperty("interest_tags")
    private List<String> interestTags;

    @JsonProperty("join_type")
    private String joinType;

    @JsonProperty("max_members")
    private Integer maxMembers;

    @JsonProperty("avatar_url")
    private String avatarUrl;
}
