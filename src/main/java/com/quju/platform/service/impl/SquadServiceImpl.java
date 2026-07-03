package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.dto.social.SquadPointsRankResp;
import com.quju.platform.entity.*;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.*;
import com.quju.platform.service.NotificationService;
import com.quju.platform.service.SquadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SquadServiceImpl implements SquadService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final TeamJoinRequestMapper teamJoinRequestMapper;
    private final TeamBlacklistMapper teamBlacklistMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

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
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }

        // 检查是否已是成员
        Long memberCount = teamMemberMapper.selectCount(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, userId));
        if (memberCount > 0) {
            throw new BusinessException(40901, "已经是小队成员");
        }

        // 检查是否已满
        if (team.getCurrentMembers() != null && team.getMaxMembers() != null
                && team.getCurrentMembers() >= team.getMaxMembers()) {
            throw new BusinessException(40902, "小队成员已满");
        }

        // 检查黑名单
        Long blacklistCount = teamBlacklistMapper.selectCount(Wrappers.<TeamBlacklistEntity>lambdaQuery()
                .eq(TeamBlacklistEntity::getTeamId, id)
                .eq(TeamBlacklistEntity::getUserId, userId));
        if (blacklistCount > 0) {
            throw new BusinessException(40301, "您已被加入小队黑名单");
        }

        if ("review".equals(team.getJoinType())) {
            TeamJoinRequestEntity request = new TeamJoinRequestEntity();
            request.setTeamId(id);
            request.setUserId(userId);
            request.setStatus("pending");
            teamJoinRequestMapper.insert(request);

            // 通知队长
            List<TeamMemberEntity> leaders = teamMemberMapper.selectList(Wrappers.<TeamMemberEntity>lambdaQuery()
                    .eq(TeamMemberEntity::getTeamId, id)
                    .eq(TeamMemberEntity::getRole, "leader"));
            for (TeamMemberEntity leader : leaders) {
                notificationService.notify(
                        leader.getUserId(),
                        "team_join_request",
                        "新的入队申请",
                        "有用户申请加入您的队伍",
                        Map.of("team_id", id, "user_id", userId, "request_id", request.getId())
                );
            }
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
    @Transactional
    public void dissolve(String id, String userId) {
        TeamEntity team = detail(id);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        if (!userId.equals(team.getLeaderId())) {
            throw new BusinessException(40300, "只有队长可以解散小队");
        }
        team.setStatus("dissolved");
        teamMapper.updateById(team);
    }

    @Override
    @Transactional
    public TeamEntity update(String id, String userId, SquadCreateReq req) {
        TeamEntity team = detail(id);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        // 检查是否为队长或管理员
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, userId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以修改小队信息");
        }

        if (req.getName() != null) team.setName(req.getName());
        if (req.getDescription() != null) team.setDescription(req.getDescription());
        if (req.getInterestTags() != null) team.setInterestTags(req.getInterestTags());
        if (req.getJoinType() != null) team.setJoinType(req.getJoinType());
        if (req.getMaxMembers() != null) team.setMaxMembers(req.getMaxMembers());
        if (req.getAvatarUrl() != null) team.setAvatarUrl(req.getAvatarUrl());
        teamMapper.updateById(team);
        return team;
    }

    @Override
    @Transactional
    public void leave(String id, String userId) {
        TeamEntity team = detail(id);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        // 队长不能直接退出，需要先解散或转让
        if (userId.equals(team.getLeaderId())) {
            throw new BusinessException(40300, "队长不能退出小队，请先转让队长或解散小队");
        }
        int deleted = teamMemberMapper.delete(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, userId));
        if (deleted == 0) {
            throw new BusinessException(40404, "您不是小队成员");
        }
        team.setCurrentMembers(team.getCurrentMembers() - 1);
        teamMapper.updateById(team);
    }

    @Override
    @Transactional
    public void changeRole(String id, String userId, String targetUserId, String newRole) {
        TeamEntity team = detail(id);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        // 只有队长可以修改角色
        if (!userId.equals(team.getLeaderId())) {
            throw new BusinessException(40300, "只有队长可以修改成员角色");
        }
        if (!"admin".equals(newRole) && !"member".equals(newRole)) {
            throw new BusinessException(40000, "无效的角色，仅支持 admin 和 member");
        }
        if (userId.equals(targetUserId)) {
            throw new BusinessException(40000, "不能修改自己的角色");
        }
        TeamMemberEntity targetMember = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, targetUserId));
        if (targetMember == null) {
            throw new BusinessException(40404, "目标用户不是小队成员");
        }
        if ("leader".equals(targetMember.getRole())) {
            throw new BusinessException(40000, "不能修改队长的角色");
        }
        targetMember.setRole(newRole);
        teamMemberMapper.updateById(targetMember);
    }

    @Override
    @Transactional
    public void removeMember(String id, String userId, String targetUserId) {
        TeamEntity team = detail(id);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        // 检查是否为队长或管理员
        TeamMemberEntity requester = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, userId));
        if (requester == null || (!"leader".equals(requester.getRole()) && !"admin".equals(requester.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以移除成员");
        }
        if (userId.equals(targetUserId)) {
            throw new BusinessException(40000, "不能移除自己");
        }
        TeamMemberEntity targetMember = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, targetUserId));
        if (targetMember == null) {
            throw new BusinessException(40404, "目标用户不是小队成员");
        }
        if ("leader".equals(targetMember.getRole())) {
            throw new BusinessException(40300, "不能移除队长");
        }
        teamMemberMapper.deleteById(targetMember.getId());
        team.setCurrentMembers(team.getCurrentMembers() - 1);
        teamMapper.updateById(team);
    }

    @Override
    public List<Map<String, Object>> members(String id) {
        TeamEntity team = detail(id);
        List<TeamMemberEntity> memberRecords = teamMemberMapper.selectList(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .orderByAsc(TeamMemberEntity::getJoinedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMemberEntity m : memberRecords) {
            UserEntity user = userMapper.selectById(m.getUserId());
            Map<String, Object> info = new HashMap<>();
            info.put("id", m.getId());
            info.put("userId", m.getUserId());
            info.put("role", m.getRole());
            info.put("joinedAt", m.getJoinedAt());
            info.put("nickname", user != null ? user.getNickname() : null);
            info.put("avatarUrl", user != null ? user.getAvatarUrl() : null);
            result.add(info);
        }
        return result;
    }

    @Override
    public List<TeamJoinRequestEntity> joinRequests(String id, String userId) {
        TeamEntity team = detail(id);
        // 只有队长和管理员可以查看
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, userId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以查看入队申请");
        }
        return teamJoinRequestMapper.selectList(Wrappers.<TeamJoinRequestEntity>lambdaQuery()
                .eq(TeamJoinRequestEntity::getTeamId, id)
                .eq(TeamJoinRequestEntity::getStatus, "pending")
                .orderByDesc(TeamJoinRequestEntity::getCreatedAt));
    }

    @Override
    @Transactional
    public void approveRequest(String id, String userId, String requestId) {
        TeamEntity team = detail(id);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        // 只有队长和管理员可以审批
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, userId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以审批入队申请");
        }

        TeamJoinRequestEntity request = teamJoinRequestMapper.selectById(requestId);
        if (request == null || !id.equals(request.getTeamId())) {
            throw new BusinessException(40404, "申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException(40001, "申请已被处理");
        }

        // 检查是否已满
        if (team.getCurrentMembers() != null && team.getMaxMembers() != null
                && team.getCurrentMembers() >= team.getMaxMembers()) {
            throw new BusinessException(40902, "小队成员已满");
        }

        request.setStatus("approved");
        request.setReviewedBy(userId);
        teamJoinRequestMapper.updateById(request);

        // 创建成员记录
        TeamMemberEntity newMember = new TeamMemberEntity();
        newMember.setTeamId(id);
        newMember.setUserId(request.getUserId());
        newMember.setRole("member");
        teamMemberMapper.insert(newMember);

        team.setCurrentMembers(team.getCurrentMembers() + 1);
        teamMapper.updateById(team);

        // 通知申请人
        notificationService.notify(
                request.getUserId(),
                "team_join_approved",
                "入队申请已通过",
                "您加入小队的申请已被批准",
                Map.of("team_id", id)
        );
    }

    @Override
    @Transactional
    public void rejectRequest(String id, String userId, String requestId) {
        TeamEntity team = detail(id);
        // 只有队长和管理员可以审批
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, userId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以审批入队申请");
        }

        TeamJoinRequestEntity request = teamJoinRequestMapper.selectById(requestId);
        if (request == null || !id.equals(request.getTeamId())) {
            throw new BusinessException(40404, "申请不存在");
        }
        if (!"pending".equals(request.getStatus())) {
            throw new BusinessException(40001, "申请已被处理");
        }

        request.setStatus("rejected");
        request.setReviewedBy(userId);
        teamJoinRequestMapper.updateById(request);

        // 通知申请人
        notificationService.notify(
                request.getUserId(),
                "team_join_rejected",
                "入队申请被拒绝",
                "您加入小队的申请已被拒绝",
                Map.of("team_id", id)
        );
    }

    @Override
    public List<SquadPointsRankResp> leaderboard(String teamId) {
        TeamEntity team = detail(teamId);
        List<TeamMemberEntity> members = teamMemberMapper.selectList(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .orderByDesc(TeamMemberEntity::getPoints));
        AtomicInteger rank = new AtomicInteger(1);
        return members.stream()
                .map(m -> {
                    UserEntity user = userMapper.selectById(m.getUserId());
                    return SquadPointsRankResp.builder()
                            .userId(m.getUserId())
                            .nickname(user != null ? user.getNickname() : "未知")
                            .points(m.getPoints() == null ? 0 : m.getPoints())
                            .rank(rank.getAndIncrement())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional
    public void addPoints(String teamId, String userId, int points) {
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, userId));
        if (member == null) {
            throw new BusinessException(40404, "用户不是小队成员");
        }
        member.setPoints((member.getPoints() == null ? 0 : member.getPoints()) + points);
        teamMemberMapper.updateById(member);
    }

    @Override
    @Transactional
    public void transferLeader(String teamId, String currentLeaderId, String newLeaderId) {
        TeamEntity team = detail(teamId);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        if (!currentLeaderId.equals(team.getLeaderId())) {
            throw new BusinessException(40300, "只有队长可以转让队长");
        }
        if (currentLeaderId.equals(newLeaderId)) {
            throw new BusinessException(40000, "不能转让给自己");
        }
        TeamMemberEntity newLeader = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, newLeaderId));
        if (newLeader == null) {
            throw new BusinessException(40404, "目标用户不是小队成员");
        }
        // 原队长降级为管理员
        TeamMemberEntity oldLeader = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, currentLeaderId));
        if (oldLeader != null) {
            oldLeader.setRole("admin");
            teamMemberMapper.updateById(oldLeader);
        }
        // 新队长升级
        newLeader.setRole("leader");
        teamMemberMapper.updateById(newLeader);
        // 更新小队队长ID
        team.setLeaderId(newLeaderId);
        teamMapper.updateById(team);
    }
}