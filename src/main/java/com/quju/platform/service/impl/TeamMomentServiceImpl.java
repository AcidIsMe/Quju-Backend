package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.TeamEntity;
import com.quju.platform.entity.TeamMomentEntity;
import com.quju.platform.entity.TeamMemberEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.TeamMapper;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.mapper.TeamMomentMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.SquadService;
import com.quju.platform.service.TeamMomentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TeamMomentServiceImpl implements TeamMomentService {

    private static final int MOMENT_POST_POINTS = 2;
    private static final int MOMENT_FEATURED_POINTS = 10;

    private final TeamMomentMapper momentMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamMapper teamMapper;
    private final UserMapper userMapper;
    private final SquadService squadService;

    @Override
    @Transactional
    public Map<String, Object> create(String teamId, String userId, String content, String imageUrl) {
        TeamEntity team = teamMapper.selectById(teamId);
        if (team == null || !"active".equals(team.getStatus())) {
            throw new BusinessException(40404, "小队不存在或已解散");
        }
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, userId));
        if (member == null) {
            throw new BusinessException(40300, "只有小队成员可以发布动态");
        }

        TeamMomentEntity moment = new TeamMomentEntity();
        moment.setTeamId(teamId);
        moment.setUserId(userId);
        moment.setContent(content);
        moment.setImageUrl(imageUrl);
        moment.setFeatured(false);
        moment.setCreatedAt(LocalDateTime.now());
        momentMapper.insert(moment);

        // 发布动态 +2 积分
        try {
            squadService.addPoints(teamId, userId, MOMENT_POST_POINTS);
        } catch (Exception ignored) {
        }

        UserEntity user = userMapper.selectById(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", moment.getId());
        result.put("team_id", moment.getTeamId());
        result.put("user_id", moment.getUserId());
        result.put("nickname", user != null ? user.getNickname() : "未知");
        result.put("avatar_url", user != null ? user.getAvatarUrl() : null);
        result.put("content", moment.getContent());
        result.put("image_url", moment.getImageUrl());
        result.put("featured", false);
        result.put("created_at", moment.getCreatedAt().toString());
        return result;
    }

    @Override
    public List<Map<String, Object>> list(String teamId, String cursor, int limit) {
        var wrapper = Wrappers.<TeamMomentEntity>lambdaQuery()
                .eq(TeamMomentEntity::getTeamId, teamId)
                .orderByDesc(TeamMomentEntity::getCreatedAt);

        if (cursor != null && !cursor.isBlank()) {
            try {
                wrapper.lt(TeamMomentEntity::getCreatedAt, LocalDateTime.parse(cursor));
            } catch (Exception ignored) {
            }
        }

        int effectiveLimit = Math.max(1, Math.min(limit <= 0 ? 20 : limit, 50));
        wrapper.last("LIMIT " + effectiveLimit);

        List<TeamMomentEntity> moments = momentMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMomentEntity m : moments) {
            UserEntity user = userMapper.selectById(m.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", m.getId());
            item.put("team_id", m.getTeamId());
            item.put("user_id", m.getUserId());
            item.put("nickname", user != null ? user.getNickname() : "未知");
            item.put("avatar_url", user != null ? user.getAvatarUrl() : null);
            item.put("content", m.getContent());
            item.put("image_url", m.getImageUrl());
            item.put("featured", Boolean.TRUE.equals(m.getFeatured()));
            item.put("created_at", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            result.add(item);
        }
        return result;
    }

    @Override
    @Transactional
    public void delete(String momentId, String userId) {
        TeamMomentEntity moment = momentMapper.selectById(momentId);
        if (moment == null) {
            throw new BusinessException(40404, "动态不存在");
        }
        if (!moment.getUserId().equals(userId)) {
            // 队长和管理员也可以删除
            TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                    .eq(TeamMemberEntity::getTeamId, moment.getTeamId())
                    .eq(TeamMemberEntity::getUserId, userId));
            if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
                throw new BusinessException(40300, "无权限删除该动态");
            }
        }
        momentMapper.deleteById(momentId);
    }

    @Override
    @Transactional
    public void feature(String momentId, String operatorUserId) {
        TeamMomentEntity moment = momentMapper.selectById(momentId);
        if (moment == null) {
            throw new BusinessException(40404, "动态不存在");
        }
        if (Boolean.TRUE.equals(moment.getFeatured())) {
            throw new BusinessException(40901, "该动态已被精选");
        }
        // 只有队长和管理员可以精选
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, moment.getTeamId())
                .eq(TeamMemberEntity::getUserId, operatorUserId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以精选动态");
        }
        moment.setFeatured(true);
        moment.setFeaturedBy(operatorUserId);
        moment.setFeaturedAt(LocalDateTime.now());
        momentMapper.updateById(moment);

        // 动态被精选 +10 积分
        try {
            squadService.addPoints(moment.getTeamId(), moment.getUserId(), MOMENT_FEATURED_POINTS);
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional
    public void unfeature(String momentId, String operatorUserId) {
        TeamMomentEntity moment = momentMapper.selectById(momentId);
        if (moment == null) {
            throw new BusinessException(40404, "动态不存在");
        }
        if (!Boolean.TRUE.equals(moment.getFeatured())) {
            throw new BusinessException(40901, "该动态未被精选");
        }
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, moment.getTeamId())
                .eq(TeamMemberEntity::getUserId, operatorUserId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以取消精选");
        }
        moment.setFeatured(false);
        moment.setFeaturedBy(null);
        moment.setFeaturedAt(null);
        momentMapper.updateById(moment);
    }
}
