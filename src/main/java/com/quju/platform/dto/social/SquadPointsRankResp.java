package com.quju.platform.dto.social;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SquadPointsRankResp {

    private String userId;
    private String nickname;
    private Integer points;
    private Integer rank;
}
