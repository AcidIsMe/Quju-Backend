package com.quju.platform.service;

import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.dto.social.SquadPointsRankResp;
import com.quju.platform.entity.TeamEntity;

import java.util.List;
import java.util.Map;

public interface SquadService {
    TeamEntity create(SquadCreateReq req, String leaderId);
    List<TeamEntity> list(String q, String interestTags, String sort, String cursor, Integer limit);
    List<Map<String, Object>> recommend(String userId, int limit);
    TeamEntity detail(String id);
    /** 获取小队详情（含队长信息、成员列表、活动数、当前用户角色） */
    Map<String, Object> detailWithMembers(String id, String viewerId);
    Map<String, Object> join(String id, String userId);
    void dissolve(String id, String userId);
    TeamEntity update(String id, String userId, SquadCreateReq req);
    void leave(String id, String userId);
    void changeRole(String id, String userId, String targetUserId, String newRole);
    void removeMember(String id, String userId, String targetUserId);
    List<Map<String, Object>> members(String id);
    List<Map<String, Object>> joinRequests(String id, String userId);
    void approveRequest(String id, String userId, String requestId);
    void rejectRequest(String id, String userId, String requestId);
    List<SquadPointsRankResp> leaderboard(String teamId);
    void addPoints(String teamId, String userId, int points);
    void transferLeader(String teamId, String currentLeaderId, String newLeaderId);
    void addToBlacklist(String teamId, String operatorUserId, String targetUserId);
    void removeFromBlacklist(String teamId, String operatorUserId, String targetUserId);
    Map<String, Object> getChatInfo(String teamId, String userId);
}
