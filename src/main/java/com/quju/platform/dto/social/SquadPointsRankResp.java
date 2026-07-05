package com.quju.platform.dto.social;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SquadPointsRankResp {

    @JsonProperty("user_id")
    private String userId;
    private String nickname;
    @JsonProperty("avatar_url")
    private String avatarUrl;
    private Integer points;
    private Integer rank;
}
