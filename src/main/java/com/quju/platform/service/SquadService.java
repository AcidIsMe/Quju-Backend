package com.quju.platform.service;

import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.entity.TeamEntity;

import java.util.List;
import java.util.Map;

public interface SquadService {
    TeamEntity create(SquadCreateReq req, String leaderId);
    List<TeamEntity> list(String q, Integer limit);
    TeamEntity detail(String id);
    Map<String, Object> join(String id, String userId);
    void dissolve(String id, String userId);
}
