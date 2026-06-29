package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.entity.TeamEntity;
import com.quju.platform.entity.TeamJoinRequestEntity;
import com.quju.platform.entity.TeamMemberEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.TeamJoinRequestMapper;
import com.quju.platform.mapper.TeamMapper;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.service.SquadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SquadServiceImpl implements SquadService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamJoinRequestMapper teamJoinRequestMapper;

    @Override
    @Transactional
    public TeamEntity create(SquadCreateReq req, String leaderId) {
        TeamEntity team = new TeamEntity();
        team.setName(req.getName());
        team.setDescription(req.getDescription());
        team.setInterestTags(req.getInterestTags() == null ? List.of() : req.getInterestTags());
        team.setJoinType(req.getJoinType() == null ? "public" : req.getJoinType());
        team.setMaxMembers(req.getMaxMembers());
        team.setCurrentMembers(1);
        team.setLeaderId(leaderId);
        team.setAvatarUrl(req.getAvatarUrl());
        team.setStatus("active");
        teamMapper.insert(team);

        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeamId(team.getId());
        member.setUserId(leaderId);
        member.setRole("leader");
        teamMemberMapper.insert(member);
        return team;
    }

    @Override
    public List<TeamEntity> list(String q, Integer limit) {
        return teamMapper.selectPage(new Page<>(1, limit == null ? 20 : limit), Wrappers.<TeamEntity>lambdaQuery()
                .eq(TeamEntity::getStatus, "active")
                .like(q != null && !q.isBlank(), TeamEntity::getName, q)
                .orderByDesc(TeamEntity::getCreatedAt)).getRecords();
    }

    @Override
    public TeamEntity detail(String id) {
        TeamEntity team = teamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException(40404, "小队不存在");
        }
        return team;
    }

    @Override
    @Transactional
    public Map<String, Object> join(String id, String userId) {
        TeamEntity team = detail(id);
        if ("review".equals(team.getJoinType())) {
            TeamJoinRequestEntity request = new TeamJoinRequestEntity();
            request.setTeamId(id);
            request.setUserId(userId);
            request.setStatus("pending");
            teamJoinRequestMapper.insert(request);
            return Map.of("status", "pending", "request_id", request.getId());
        }
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeamId(id);
        member.setUserId(userId);
        member.setRole("member");
        teamMemberMapper.insert(member);
        team.setCurrentMembers(team.getCurrentMembers() + 1);
        teamMapper.updateById(team);
        return Map.of("status", "joined");
    }

    @Override
    public void dissolve(String id, String userId) {
        TeamEntity team = detail(id);
        team.setStatus("dissolved");
        teamMapper.updateById(team);
    }
}
