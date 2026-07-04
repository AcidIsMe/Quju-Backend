package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.TeamAnnouncementEntity;
import com.quju.platform.entity.TeamEntity;
import com.quju.platform.entity.TeamMemberEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.TeamAnnouncementMapper;
import com.quju.platform.mapper.TeamMapper;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.TeamAnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TeamAnnouncementServiceImpl implements TeamAnnouncementService {

    private final TeamAnnouncementMapper teamAnnouncementMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public void create(String teamId, String operatorId, String content) {
        verifyLeaderOrAdmin(teamId, operatorId);
        TeamAnnouncementEntity announcement = new TeamAnnouncementEntity();
        announcement.setTeamId(teamId);
        announcement.setContent(content);
        announcement.setPinned(false);
        announcement.setCreatedBy(operatorId);
        teamAnnouncementMapper.insert(announcement);
    }

    @Override
    public List<Map<String, Object>> list(String teamId) {
        TeamEntity team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(40404, "小队不存在");
        }
        List<TeamAnnouncementEntity> announcements = teamAnnouncementMapper.selectList(
                Wrappers.<TeamAnnouncementEntity>lambdaQuery()
                        .eq(TeamAnnouncementEntity::getTeamId, teamId)
                        .orderByDesc(TeamAnnouncementEntity::getPinned)
                        .orderByDesc(TeamAnnouncementEntity::getCreatedAt));

        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamAnnouncementEntity a : announcements) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("content", a.getContent());
            item.put("pinned", a.getPinned() != null && a.getPinned());
            item.put("created_at", a.getCreatedAt());

            UserEntity creator = a.getCreatedBy() == null ? null : userMapper.selectById(a.getCreatedBy());
            if (creator != null) {
                Map<String, Object> creatorMap = new LinkedHashMap<>();
                creatorMap.put("id", creator.getId());
                creatorMap.put("nickname", creator.getNickname());
                creatorMap.put("avatar_url", creator.getAvatarUrl());
                item.put("created_by", creatorMap);
            } else {
                item.put("created_by", null);
            }
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional
    public void delete(String teamId, String operatorId, String announcementId) {
        verifyLeaderOrAdmin(teamId, operatorId);
        TeamAnnouncementEntity announcement = teamAnnouncementMapper.selectById(announcementId);
        if (announcement == null || !teamId.equals(announcement.getTeamId())) {
            throw new BusinessException(40404, "公告不存在");
        }
        teamAnnouncementMapper.deleteById(announcementId);
    }

    @Override
    @Transactional
    public void togglePin(String teamId, String operatorId, String announcementId) {
        verifyLeaderOrAdmin(teamId, operatorId);
        TeamAnnouncementEntity announcement = teamAnnouncementMapper.selectById(announcementId);
        if (announcement == null || !teamId.equals(announcement.getTeamId())) {
            throw new BusinessException(40404, "公告不存在");
        }
        announcement.setPinned(!(announcement.getPinned() != null && announcement.getPinned()));
        teamAnnouncementMapper.updateById(announcement);
    }

    /**
     * 验证操作者是队长或管理员，同时验证小队存在
     */
    private void verifyLeaderOrAdmin(String teamId, String operatorId) {
        TeamEntity team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BusinessException(40404, "小队不存在");
        }
        TeamMemberEntity member = teamMemberMapper.selectOne(
                Wrappers.<TeamMemberEntity>lambdaQuery()
                        .eq(TeamMemberEntity::getTeamId, teamId)
                        .eq(TeamMemberEntity::getUserId, operatorId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以管理公告");
        }
    }
}
