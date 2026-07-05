package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.dto.social.SquadPointsRankResp;
import com.quju.platform.entity.*;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.*;
import com.quju.platform.service.ImService;
import com.quju.platform.service.NotificationService;
import com.quju.platform.service.SquadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final ActivityMapper activityMapper;
    private final GroupChatReadMarkerMapper groupChatReadMarkerMapper;
    private final ImMessageMapper imMessageMapper;
    private final NotificationService notificationService;
    private final ImService imService;

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

        // 自动为队长创建群聊已读标记
        ensureChatMarker(team.getId(), leaderId);

        // 在小队群聊中发送欢迎消息
        sendWelcomeMessage(team.getId(), leaderId, "欢迎来到「" + team.getName() + "」小队！");

        return team;
    }

    @Override
    public List<TeamEntity> list(String q, String interestTags, String sort, String cursor, Integer limit) {
        var wrapper = Wrappers.<TeamEntity>lambdaQuery()
                .eq(TeamEntity::getStatus, "active")
                .like(q != null && !q.isBlank(), TeamEntity::getName, q);

        // 按兴趣标签过滤：标签之间为 OR 关系
        if (interestTags != null && !interestTags.isBlank()) {
            String[] tags = interestTags.split(",");
            wrapper.and(w -> {
                boolean first = true;
                for (String raw : tags) {
                    String tag = raw.trim();
                    if (tag.isEmpty()) continue;
                    if (first) {
                        w.apply("JSON_CONTAINS(interest_tags, {0})", "\"" + tag + "\"");
                        first = false;
                    } else {
                        w.or().apply("JSON_CONTAINS(interest_tags, {0})", "\"" + tag + "\"");
                    }
                }
            });
        }

        // 排序
        if ("popular".equals(sort)) {
            wrapper.orderByDesc(TeamEntity::getCurrentMembers);
        } else {
            wrapper.orderByDesc(TeamEntity::getCreatedAt);
        }

        // 游标分页
        if (cursor != null && !cursor.isBlank()) {
            if ("popular".equals(sort)) {
                try {
                    wrapper.lt(TeamEntity::getCurrentMembers, Integer.parseInt(cursor));
                } catch (NumberFormatException ignored) {
                }
            } else {
                try {
                    wrapper.lt(TeamEntity::getCreatedAt, LocalDateTime.parse(cursor));
                } catch (Exception ignored) {
                }
            }
        }

        int effectiveLimit = Math.max(1, Math.min(limit == null ? 20 : limit, 50));
        wrapper.last("LIMIT " + effectiveLimit);

        return teamMapper.selectList(wrapper);
    }

    @Override
    public List<Map<String, Object>> recommend(String userId, int limit) {
        UserEntity user = userMapper.selectById(userId);
        List<String> userTags = (user != null && user.getInterestTags() != null)
                ? user.getInterestTags() : List.of();

        // 收集用户已加入或已被拉黑的小队ID，推荐时排除
        List<String> excludeIds = new ArrayList<>();
        List<TeamMemberEntity> userMemberships = teamMemberMapper.selectList(
                Wrappers.<TeamMemberEntity>lambdaQuery()
                        .eq(TeamMemberEntity::getUserId, userId));
        for (TeamMemberEntity m : userMemberships) {
            excludeIds.add(m.getTeamId());
        }
        List<TeamBlacklistEntity> blacklists = teamBlacklistMapper.selectList(
                Wrappers.<TeamBlacklistEntity>lambdaQuery()
                        .eq(TeamBlacklistEntity::getUserId, userId));
        for (TeamBlacklistEntity b : blacklists) {
            if (!excludeIds.contains(b.getTeamId())) {
                excludeIds.add(b.getTeamId());
            }
        }

        // 查询活跃小队
        var wrapper = Wrappers.<TeamEntity>lambdaQuery()
                .eq(TeamEntity::getStatus, "active");
        if (!excludeIds.isEmpty()) {
            wrapper.notIn(TeamEntity::getId, excludeIds);
        }
        List<TeamEntity> candidates = teamMapper.selectList(wrapper);

        if (candidates.isEmpty()) {
            return List.of();
        }

        // 批量统计各小队的活动数量
        Map<String, Long> activityCounts = new HashMap<>();
        for (TeamEntity team : candidates) {
            long count = activityMapper.selectCount(
                    Wrappers.<ActivityEntity>lambdaQuery()
                            .eq(ActivityEntity::getTeamId, team.getId())
                            .eq(ActivityEntity::getTeamActivity, true));
            activityCounts.put(team.getId(), count);
        }

        // 打分：匹配标签数×10 + 当前成员数×0.01 + 活动数×3
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamEntity team : candidates) {
            int matchCount = 0;
            if (userTags != null && team.getInterestTags() != null) {
                for (String tag : team.getInterestTags()) {
                    if (userTags.contains(tag)) {
                        matchCount++;
                    }
                }
            }
            double score = (matchCount * 10.0)
                    + ((team.getCurrentMembers() != null ? team.getCurrentMembers() : 0) * 0.01)
                    + (activityCounts.getOrDefault(team.getId(), 0L) * 3.0);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", team.getId());
            item.put("name", team.getName());
            item.put("description", team.getDescription());
            item.put("interest_tags", team.getInterestTags());
            item.put("join_type", team.getJoinType());
            item.put("current_members", team.getCurrentMembers());
            item.put("max_members", team.getMaxMembers());
            item.put("avatar_url", team.getAvatarUrl());
            item.put("status", team.getStatus());
            item.put("match_score", Math.round(score * 100.0) / 100.0);
            result.add(item);
        }

        // 按得分降序排列
        result.sort((a, b) -> Double.compare(
                (Double) b.get("match_score"),
                (Double) a.get("match_score")));

        if (result.size() > limit) {
            return result.subList(0, limit);
        }
        return result;
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
    public Map<String, Object> detailWithMembers(String id, String viewerId) {
        TeamEntity team = detail(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", team.getId());
        result.put("name", team.getName());
        result.put("description", team.getDescription());
        result.put("interest_tags", team.getInterestTags());
        result.put("join_type", team.getJoinType());
        result.put("max_members", team.getMaxMembers());
        result.put("current_members", team.getCurrentMembers());
        result.put("avatar_url", team.getAvatarUrl());
        result.put("status", team.getStatus());
        result.put("created_at", team.getCreatedAt());
        result.put("updated_at", team.getUpdatedAt());

        // 队长信息
        UserEntity leader = team.getLeaderId() == null ? null : userMapper.selectById(team.getLeaderId());
        if (leader != null) {
            Map<String, Object> leaderMap = new LinkedHashMap<>();
            leaderMap.put("id", leader.getId());
            leaderMap.put("nickname", leader.getNickname());
            leaderMap.put("avatar_url", leader.getAvatarUrl());
            result.put("leader", leaderMap);
        } else {
            result.put("leader", null);
        }

        // 活动数统计
        long activityCount = activityMapper.selectCount(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ActivityEntity>lambdaQuery()
                        .eq(ActivityEntity::getTeamId, id)
                        .eq(ActivityEntity::getTeamActivity, true));
        result.put("activity_count", activityCount);

        // 小队活动列表
        List<ActivityEntity> teamActivities = activityMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ActivityEntity>lambdaQuery()
                        .eq(ActivityEntity::getTeamId, id)
                        .eq(ActivityEntity::getTeamActivity, true)
                        .orderByDesc(ActivityEntity::getCreatedAt));
        List<Map<String, Object>> activityList = new ArrayList<>();
        for (ActivityEntity a : teamActivities) {
            Map<String, Object> ai = new LinkedHashMap<>();
            ai.put("id", a.getId());
            ai.put("title", a.getTitle());
            ai.put("description", a.getDescription());
            ai.put("tags", a.getTags());
            ai.put("activity_type", a.getActivityType());
            ai.put("cover_image_url", a.getCoverImageUrl());
            ai.put("start_time", a.getStartTime());
            ai.put("end_time", a.getEndTime());
            ai.put("registration_deadline", a.getRegistrationDeadline());
            ai.put("max_participants", a.getMaxParticipants());
            ai.put("current_participants", a.getCurrentParticipants());
            ai.put("fee_type", a.getFeeType());
            ai.put("fee_amount", a.getFeeAmount());
            ai.put("location_name", a.getLocationName());
            ai.put("location_lat", a.getLocationLat());
            ai.put("location_lng", a.getLocationLng());
            ai.put("status", a.getStatus());
            ai.put("created_at", a.getCreatedAt());
            activityList.add(ai);
        }
        result.put("activities", activityList);

        // 成员列表
        List<TeamMemberEntity> memberRecords = teamMemberMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<TeamMemberEntity>lambdaQuery()
                        .eq(TeamMemberEntity::getTeamId, id)
                        .orderByAsc(TeamMemberEntity::getJoinedAt));
        List<Map<String, Object>> memberList = new ArrayList<>();
        for (TeamMemberEntity m : memberRecords) {
            UserEntity u = userMapper.selectById(m.getUserId());
            Map<String, Object> mi = new LinkedHashMap<>();
            mi.put("user_id", m.getUserId());
            mi.put("role", m.getRole());
            mi.put("points", m.getPoints());
            mi.put("nickname", u != null ? u.getNickname() : null);
            mi.put("avatar_url", u != null ? u.getAvatarUrl() : null);
            mi.put("joined_at", m.getJoinedAt());
            memberList.add(mi);
        }
        result.put("members", memberList);

        // 当前查看者的角色（可为 null）
        if (viewerId != null) {
            TeamMemberEntity viewerMember = teamMemberMapper.selectOne(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<TeamMemberEntity>lambdaQuery()
                            .eq(TeamMemberEntity::getTeamId, id)
                            .eq(TeamMemberEntity::getUserId, viewerId));
            result.put("my_role", viewerMember != null ? viewerMember.getRole() : null);
        }

        return result;
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

        // 检查是否已有待处理的入队申请
        if ("review".equals(team.getJoinType())) {
            Long pendingCount = teamJoinRequestMapper.selectCount(Wrappers.<TeamJoinRequestEntity>lambdaQuery()
                    .eq(TeamJoinRequestEntity::getTeamId, id)
                    .eq(TeamJoinRequestEntity::getUserId, userId)
                    .eq(TeamJoinRequestEntity::getStatus, "pending"));
            if (pendingCount > 0) {
                throw new BusinessException(40903, "您已提交过入队申请，请等待审核");
            }
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
        ensureChatMarker(id, userId);
        team.setCurrentMembers(team.getCurrentMembers() + 1);
        teamMapper.updateById(team);

        // 在小队群聊中发送欢迎消息
        UserEntity newMember = userMapper.selectById(userId);
        String nickname = newMember != null ? newMember.getNickname() : "新成员";
        sendWelcomeMessage(id, team.getLeaderId(), "欢迎 " + nickname + " 加入「" + team.getName() + "」小队！");

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
    public List<Map<String, Object>> joinRequests(String id, String userId) {
        TeamEntity team = detail(id);
        // 只有队长和管理员可以查看
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, id)
                .eq(TeamMemberEntity::getUserId, userId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以查看入队申请");
        }
        List<TeamJoinRequestEntity> requests = teamJoinRequestMapper.selectList(
                Wrappers.<TeamJoinRequestEntity>lambdaQuery()
                        .eq(TeamJoinRequestEntity::getTeamId, id)
                        .eq(TeamJoinRequestEntity::getStatus, "pending")
                        .orderByDesc(TeamJoinRequestEntity::getCreatedAt));

        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamJoinRequestEntity req : requests) {
            UserEntity applicant = userMapper.selectById(req.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", req.getId());
            item.put("team_id", req.getTeamId());
            item.put("user_id", req.getUserId());
            item.put("status", req.getStatus());
            item.put("nickname", applicant != null ? applicant.getNickname() : null);
            item.put("avatar_url", applicant != null ? applicant.getAvatarUrl() : null);
            item.put("created_at", req.getCreatedAt());
            result.add(item);
        }
        return result;
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
        ensureChatMarker(id, request.getUserId());

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

        // 在小队群聊中发送欢迎消息
        UserEntity applicant = userMapper.selectById(request.getUserId());
        String applicantNickname = applicant != null ? applicant.getNickname() : "新成员";
        sendWelcomeMessage(id, team.getLeaderId(), "欢迎 " + applicantNickname + " 加入「" + team.getName() + "」小队！");
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

    @Override
    @Transactional
    public void addToBlacklist(String teamId, String operatorUserId, String targetUserId) {
        TeamEntity team = detail(teamId);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        // 只有队长或管理员可以操作黑名单
        TeamMemberEntity operator = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, operatorUserId));
        if (operator == null || (!"leader".equals(operator.getRole()) && !"admin".equals(operator.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以管理黑名单");
        }
        // 不能将队长加入黑名单
        if (targetUserId.equals(team.getLeaderId())) {
            throw new BusinessException(40000, "不能将队长加入黑名单");
        }
        // 检查是否已在黑名单中
        Long count = teamBlacklistMapper.selectCount(Wrappers.<TeamBlacklistEntity>lambdaQuery()
                .eq(TeamBlacklistEntity::getTeamId, teamId)
                .eq(TeamBlacklistEntity::getUserId, targetUserId));
        if (count > 0) {
            throw new BusinessException(40901, "该用户已在黑名单中");
        }
        // 加入黑名单
        TeamBlacklistEntity blacklist = new TeamBlacklistEntity();
        blacklist.setTeamId(teamId);
        blacklist.setUserId(targetUserId);
        teamBlacklistMapper.insert(blacklist);
        // 如果是成员，同时移出小队
        int deleted = teamMemberMapper.delete(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, targetUserId));
        if (deleted > 0) {
            team.setCurrentMembers(team.getCurrentMembers() - 1);
            teamMapper.updateById(team);
        }
    }

    @Override
    @Transactional
    public void removeFromBlacklist(String teamId, String operatorUserId, String targetUserId) {
        TeamEntity team = detail(teamId);
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队已解散");
        }
        // 只有队长或管理员可以操作黑名单
        TeamMemberEntity operator = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, operatorUserId));
        if (operator == null || (!"leader".equals(operator.getRole()) && !"admin".equals(operator.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以管理黑名单");
        }
        int deleted = teamBlacklistMapper.delete(Wrappers.<TeamBlacklistEntity>lambdaQuery()
                .eq(TeamBlacklistEntity::getTeamId, teamId)
                .eq(TeamBlacklistEntity::getUserId, targetUserId));
        if (deleted == 0) {
            throw new BusinessException(40404, "该用户不在黑名单中");
        }
    }

    // ── 群聊 ──

    private void ensureChatMarker(String teamId, String userId) {
        Long exists = groupChatReadMarkerMapper.selectCount(
                Wrappers.<GroupChatReadMarkerEntity>lambdaQuery()
                        .eq(GroupChatReadMarkerEntity::getGroupId, teamId)
                        .eq(GroupChatReadMarkerEntity::getUserId, userId));
        if (exists == 0) {
            GroupChatReadMarkerEntity marker = new GroupChatReadMarkerEntity();
            marker.setGroupId(teamId);
            marker.setUserId(userId);
            marker.setLastReadAt(LocalDateTime.now());
            groupChatReadMarkerMapper.insert(marker);
        }
    }

    @Override
    public Map<String, Object> getChatInfo(String teamId, String userId) {
        TeamEntity team = detail(teamId);
        // 校验成员身份
        Long isMember = teamMemberMapper.selectCount(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, userId));
        if (isMember == 0) {
            throw new BusinessException(40300, "只有小队成员可以查看群聊");
        }

        String entityId = "team:" + teamId;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("team_id", teamId);
        result.put("team_name", team.getName());
        result.put("entity_type", "group");
        result.put("entity_id", entityId);

        // 最新一条消息
        ImMessageEntity lastMsg = imMessageMapper.selectOne(Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, "group")
                .eq(ImMessageEntity::getEntityId, entityId)
                .orderByDesc(ImMessageEntity::getCreatedAt)
                .last("LIMIT 1"));
        if (lastMsg != null) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("id", lastMsg.getId());
            msg.put("content", lastMsg.getContent());
            msg.put("sender_id", lastMsg.getSenderId());
            msg.put("type", lastMsg.getType());
            msg.put("created_at", lastMsg.getCreatedAt());
            UserEntity sender = userMapper.selectById(lastMsg.getSenderId());
            msg.put("sender_nickname", sender != null ? sender.getNickname() : null);
            result.put("last_message", msg);
        }

        // 未读数
        GroupChatReadMarkerEntity marker = groupChatReadMarkerMapper.selectOne(
                Wrappers.<GroupChatReadMarkerEntity>lambdaQuery()
                        .eq(GroupChatReadMarkerEntity::getGroupId, teamId)
                        .eq(GroupChatReadMarkerEntity::getUserId, userId));
        LocalDateTime since = marker != null && marker.getLastReadAt() != null
                ? marker.getLastReadAt() : LocalDateTime.now().minusYears(1);
        Long unread = imMessageMapper.selectCount(Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, "group")
                .eq(ImMessageEntity::getEntityId, entityId)
                .ne(ImMessageEntity::getSenderId, userId)
                .gt(ImMessageEntity::getCreatedAt, since));
        result.put("unread_count", unread);

        return result;
    }

    /**
     * 在小队群聊中发送一条系统欢迎消息
     */
    private void sendWelcomeMessage(String teamId, String senderId, String content) {
        try {
            ImMessageDto dto = new ImMessageDto();
            dto.setEntityType("group");
            dto.setEntityId("team:" + teamId);
            dto.setType("text");
            dto.setContent(content);
            imService.send(dto, senderId);
        } catch (Exception e) {
            // 欢迎消息发送失败不影响主流程
        }
    }
}