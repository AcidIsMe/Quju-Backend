package com.quju.platform.service;

import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.dto.social.SquadPointsRankResp;
import com.quju.platform.entity.TeamEntity;
import com.quju.platform.entity.TeamJoinRequestEntity;

import java.util.List;
import java.util.Map;

public interface SquadService {
    TeamEntity create(SquadCreateReq req, String leaderId);
    List<TeamEntity> list(String q, Integer limit);
    TeamEntity detail(String id);
    Map<String, Object> join(String id, String userId);
    void dissolve(String id, String userId);
    TeamEntity update(String id, String userId, SquadCreateReq req);
    void leave(String id, String userId);
    void changeRole(String id, String userId, String targetUserId, String newRole);
    void removeMember(String id, String userId, String targetUserId);
    List<Map<String, Object>> members(String id);
    List<TeamJoinRequestEntity> joinRequests(String id, String userId);
    void approveRequest(String id, String userId, String requestId);
    void rejectRequest(String id, String userId, String requestId);
    List<SquadPointsRankResp> leaderboard(String teamId);
    void addPoints(String teamId, String userId, int points);
    void transferLeader(String teamId, String currentLeaderId, String newLeaderId);
}
