package com.quju.platform.service;

import com.quju.platform.dto.activity.ActivityQueryReq;
import com.quju.platform.dto.activity.HomeStatsDTO;
import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.TeamEntity;

public interface DiscoveryService {
    CursorPage<ActivityEntity> latest(ActivityQueryReq req);
    CursorPage<ActivityEntity> recommended(ActivityQueryReq req);
    CursorPage<ActivityEntity> search(ActivityQueryReq req);
    CursorPage<ActivityEntity> nearby(ActivityQueryReq req);
    CursorPage<ActivityEntity> mapBox(ActivityQueryReq req);
    CursorPage<TeamEntity> recommendedTeams(String cursor, int limit);
    CursorPage<TeamEntity> latestTeams(String cursor, int limit);
    HomeStatsDTO getHomeStats();
}
