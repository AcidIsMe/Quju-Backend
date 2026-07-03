package com.quju.platform.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.component.statemachine.ActivityStateMachine;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.*;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.*;
import com.quju.platform.service.ActivityService;
import com.quju.platform.service.MerchantService;
import com.quju.platform.service.NotificationService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class OpsAdminController {

    private final UserMapper userMapper;
    private final UserBanMapper userBanMapper;
    private final MerchantProfileMapper merchantProfileMapper;
    private final MerchantService merchantService;
    private final ActivityMapper activityMapper;
    private final ActivityService activityService;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ActivityStateMachine activityStateMachine;
    private final NotificationService notificationService;

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("users", userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()));
        stats.put("activities", activityMapper.selectCount(Wrappers.<ActivityEntity>lambdaQuery()));
        stats.put("pending_reviews", activityMapper.selectCount(Wrappers.<ActivityEntity>lambdaQuery()
                .in(ActivityEntity::getStatus, List.of("pending_ai_review", "pending_manual_review"))));
        stats.put("merchants", merchantProfileMapper.selectCount(Wrappers.<MerchantProfileEntity>lambdaQuery()));
        stats.put("pending_merchants", merchantProfileMapper.selectCount(Wrappers.<MerchantProfileEntity>lambdaQuery()
                .eq(MerchantProfileEntity::getAuditStatus, "pending")));

        List<Map<String, Object>> recentActivities = activityMapper.selectList(Wrappers.<ActivityEntity>lambdaQuery()
                        .orderByDesc(ActivityEntity::getCreatedAt)
                        .orderByDesc(ActivityEntity::getId)
                        .last("LIMIT 5"))
                .stream()
                .map(this::dashboardActivityItem)
                .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stats", stats);
        data.put("recent_activities", recentActivities);
        return ApiResponse.ok(data);
    }

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String role,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "10") Integer size) {
        int pageSize = clampPageSize(size);
        int offset = (Math.max(1, page) - 1) * pageSize;
        var wrapper = Wrappers.<UserEntity>lambdaQuery()
                .and(q != null && !q.isBlank(), w -> w.like(UserEntity::getEmail, q).or().like(UserEntity::getNickname, q))
                .eq(role != null, UserEntity::getRole, role)
                .eq(status != null, UserEntity::getStatus, status);
        long total = userMapper.selectCount(wrapper);
        wrapper.orderByDesc(UserEntity::getCreatedAt).orderByDesc(UserEntity::getId);
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<Map<String, Object>> data = userMapper.selectList(wrapper).stream().map(this::userItem).toList();
        return ApiResponse.page(data, pageInfo(total, page, pageSize));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserEntity> user(@PathVariable String id) {
        return ApiResponse.ok(userMapper.selectById(id));
    }

    @PostMapping("/users/{id}/ban")
    public ApiResponse<Void> ban(@PathVariable String id,
                                 @RequestBody Map<String, String> body) {
        UserBanEntity ban = new UserBanEntity();
        ban.setUserId(id);
        ban.setReason(body.getOrDefault("reason", "违规"));
        ban.setBannedBy(SecurityUtil.requireCurrentUserId());
        ban.setBannedAt(LocalDateTime.now());
        ban.setActive(true);
        userBanMapper.insert(ban);
        UserEntity user = userMapper.selectById(id);
        if (user != null) {
            user.setStatus("banned");
            userMapper.updateById(user);
        }
        return ApiResponse.ok();
    }

    @PostMapping("/users/{id}/unban")
    public ApiResponse<Void> unban(@PathVariable String id) {
        userBanMapper.selectList(Wrappers.<UserBanEntity>lambdaQuery()
                        .eq(UserBanEntity::getUserId, id)
                        .eq(UserBanEntity::getActive, true))
                .forEach(ban -> {
                    ban.setActive(false);
                    ban.setRevokedBy(SecurityUtil.requireCurrentUserId());
                    ban.setRevokedAt(LocalDateTime.now());
                    userBanMapper.updateById(ban);
                });
        UserEntity user = userMapper.selectById(id);
        if (user != null) {
            user.setStatus("active");
            userMapper.updateById(user);
        }
        return ApiResponse.ok();
    }

    @GetMapping("/merchants/pending")
    public ApiResponse<List<Map<String, Object>>> pendingMerchants(@RequestParam(defaultValue = "1") Integer page,
                                                                   @RequestParam(defaultValue = "10") Integer size) {
        int pageSize = clampPageSize(size);
        int offset = (Math.max(1, page) - 1) * pageSize;
        var wrapper = Wrappers.<MerchantProfileEntity>lambdaQuery()
                .eq(MerchantProfileEntity::getAuditStatus, "pending");
        long total = merchantProfileMapper.selectCount(wrapper);
        wrapper.orderByDesc(MerchantProfileEntity::getCreatedAt).orderByDesc(MerchantProfileEntity::getId);
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<Map<String, Object>> data = merchantProfileMapper.selectList(wrapper).stream().map(this::merchantItem).toList();
        return ApiResponse.page(data, pageInfo(total, page, pageSize));
    }

    @PostMapping("/merchants/{id}/approve")
    public ApiResponse<Void> approveMerchant(@PathVariable String id) {
        merchantService.approve(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/merchants/{id}/reject")
    public ApiResponse<Void> rejectMerchant(@PathVariable String id,
                                            @RequestBody Map<String, String> body) {
        merchantService.reject(id, body.get("reason"), SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/activities")
    public ApiResponse<List<Map<String, Object>>> activities(@RequestParam(required = false) String status,
                                                             @RequestParam(required = false) String q,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "10") Integer size) {
        int pageSize = clampPageSize(size);
        int offset = (Math.max(1, page) - 1) * pageSize;
        var wrapper = Wrappers.<ActivityEntity>lambdaQuery()
                .eq(status != null, ActivityEntity::getStatus, status)
                .and(q != null && !q.isBlank(), w -> w.like(ActivityEntity::getTitle, q).or().like(ActivityEntity::getDescription, q));
        long total = activityMapper.selectCount(wrapper);
        wrapper.orderByDesc(ActivityEntity::getCreatedAt).orderByDesc(ActivityEntity::getId);
        wrapper.last("LIMIT " + offset + "," + pageSize);
        List<Map<String, Object>> data = activityMapper.selectList(wrapper).stream().map(this::activityItem).toList();
        return ApiResponse.page(data, pageInfo(total, page, pageSize));
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<Map<String, Object>> activity(@PathVariable String id) {
        ActivityEntity activity = activityMapper.selectById(id);
        if (activity == null) {
            return ApiResponse.fail(404, "活动不存在");
        }
        return ApiResponse.ok(activityDetailItem(activity));
    }

    @PostMapping("/activities/{id}/review")
    public ApiResponse<ActivityEntity> reviewActivity(@PathVariable String id,
                                                      @RequestBody Map<String, String> body) {
        String adminId = SecurityUtil.requireCurrentUserId();
        ActivityEntity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        // 状态守卫：只有待审核状态才能审核
        activityStateMachine.validateReviewable(activity.getStatus());

        String action = body.getOrDefault("action", "approve");
        String reason = body.get("reason");
        String newStatus;
        String notifyTitle;
        String notifyContent;

        switch (action) {
            case "approve" -> {
                newStatus = activityStateMachine.approve();
                notifyTitle = "活动审核通过";
                notifyContent = "您的活动「" + activity.getTitle() + "」已通过管理员审核并发布。";
            }
            case "request_changes" -> {
                if (reason == null || reason.isBlank()) {
                    throw new BusinessException(40015, "退回修改时必须填写原因");
                }
                newStatus = activityStateMachine.requestChanges();
                notifyTitle = "活动需要修改";
                notifyContent = "您的活动「" + activity.getTitle() + "」被退回修改，原因：" + reason;
            }
            default -> {
                // reject
                if (reason == null || reason.isBlank()) {
                    throw new BusinessException(40016, "驳回活动时必须填写原因");
                }
                newStatus = activityStateMachine.reject();
                notifyTitle = "活动审核驳回";
                notifyContent = "您的活动「" + activity.getTitle() + "」未通过审核，原因：" + reason;
            }
        }

        activity.setStatus(newStatus);
        activity.setReviewReason(reason);
        activity.setReviewedBy(adminId);
        activity.setReviewedAt(LocalDateTime.now());
        activityMapper.updateById(activity);

        // 通知活动创建者（US13 AC2）
        if (activity.getCreatorId() != null) {
            notificationService.notify(activity.getCreatorId(), "activity_review",
                    notifyTitle, notifyContent, Map.of("activity_id", activity.getId()));
        }
        return ApiResponse.ok(activity);
    }

    @PostMapping("/activities/{id}/take-down")
    public ApiResponse<Void> takeDown(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        ActivityEntity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        // 状态守卫：只有已发布的活动才能下架
        activityStateMachine.validateTakeDownable(activity.getStatus());
        // 原因必填（US40 AC3）
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(40017, "下架活动时必须填写原因");
        }

        activity.setStatus(activityStateMachine.takeDown());
        activity.setReviewReason(reason);
        activity.setReviewedBy(SecurityUtil.requireCurrentUserId());
        activity.setReviewedAt(LocalDateTime.now());
        activityMapper.updateById(activity);

        // 通知活动创建者
        if (activity.getCreatorId() != null) {
            notificationService.notify(activity.getCreatorId(), "activity_taken_down",
                    "活动已下架", "您的活动「" + activity.getTitle() + "」已被管理员下架，原因：" + reason,
                    Map.of("activity_id", activity.getId()));
        }
        return ApiResponse.ok();
    }

    @PostMapping("/activities/{id}/restore")
    public ApiResponse<Void> restoreActivity(@PathVariable String id) {
        ActivityEntity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        // 状态守卫：只有已下架的活动才能恢复
        activityStateMachine.validateRestorable(activity.getStatus());

        activity.setStatus("published");
        activityMapper.updateById(activity);

        // 通知活动创建者
        if (activity.getCreatorId() != null) {
            notificationService.notify(activity.getCreatorId(), "activity_restored",
                    "活动已恢复", "您的活动「" + activity.getTitle() + "」已恢复上线。",
                    Map.of("activity_id", activity.getId()));
        }
        return ApiResponse.ok();
    }

    @GetMapping("/teams")
    public ApiResponse<List<Map<String, Object>>> teams(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(defaultValue = "20") Integer limit) {
        int size = normalizedLimit(limit);
        var wrapper = Wrappers.<TeamEntity>lambdaQuery()
                .like(q != null && !q.isBlank(), TeamEntity::getName, q)
                .eq(status != null, TeamEntity::getStatus, status);
        applyTeamCursor(wrapper, cursor);
        wrapper.orderByDesc(TeamEntity::getCreatedAt).orderByDesc(TeamEntity::getId);
        List<TeamEntity> rows = teamMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        List<TeamEntity> pageRows = trimPage(rows, size);
        List<Map<String, Object>> enriched = pageRows.stream().map(team -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", team.getId());
            item.put("name", team.getName());
            item.put("description", team.getDescription());
            item.put("interest_tags", team.getInterestTags());
            item.put("join_type", team.getJoinType());
            item.put("max_members", team.getMaxMembers());
            item.put("current_members", team.getCurrentMembers());
            item.put("status", team.getStatus());
            item.put("created_at", team.getCreatedAt());
            UserEntity leader = team.getLeaderId() == null ? null : userMapper.selectById(team.getLeaderId());
            Map<String, Object> leaderInfo = new LinkedHashMap<>();
            if (leader != null) {
                leaderInfo.put("id", leader.getId());
                leaderInfo.put("nickname", leader.getNickname());
                leaderInfo.put("avatar_url", leader.getAvatarUrl());
            }
            item.put("leader", leader == null ? null : leaderInfo);
            long activityCount = activityMapper.selectCount(Wrappers.<ActivityEntity>lambdaQuery()
                    .eq(ActivityEntity::getTeamId, team.getId())
                    .eq(ActivityEntity::getTeamActivity, true));
            item.put("activity_count", activityCount);
            return item;
        }).toList();
        return ApiResponse.page(enriched, pagination(rows, pageRows, size, team -> cursorOf(team.getCreatedAt(), team.getId())));
    }

    @GetMapping("/teams/{id}")
    public ApiResponse<Map<String, Object>> team(@PathVariable String id) {
        TeamEntity team = teamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException(40404, "小队不存在");
        }
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
        Map<String, Object> leaderInfo = new LinkedHashMap<>();
        if (leader != null) {
            leaderInfo.put("id", leader.getId());
            leaderInfo.put("nickname", leader.getNickname());
            leaderInfo.put("avatar_url", leader.getAvatarUrl());
            leaderInfo.put("email", leader.getEmail());
        }
        result.put("leader", leader == null ? null : leaderInfo);
        // 活动数统计
        long activityCount = activityMapper.selectCount(Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getTeamId, id)
                .eq(ActivityEntity::getTeamActivity, true));
        result.put("activity_count", activityCount);
        // 成员列表（精简）
        List<TeamMemberEntity> members = teamMemberMapper.selectList(
                Wrappers.<TeamMemberEntity>lambdaQuery()
                        .eq(TeamMemberEntity::getTeamId, id)
                        .orderByAsc(TeamMemberEntity::getJoinedAt));
        result.put("members", members.stream().map(m -> {
            UserEntity u = userMapper.selectById(m.getUserId());
            Map<String, Object> mi = new LinkedHashMap<>();
            mi.put("user_id", m.getUserId());
            mi.put("role", m.getRole());
            mi.put("points", m.getPoints());
            mi.put("nickname", u != null ? u.getNickname() : null);
            mi.put("avatar_url", u != null ? u.getAvatarUrl() : null);
            mi.put("email", u != null ? u.getEmail() : null);
            mi.put("joined_at", m.getJoinedAt());
            return mi;
        }).toList());
        return ApiResponse.ok(result);
    }

    @GetMapping("/teams/{id}/members")
    public ApiResponse<List<Map<String, Object>>> teamMembers(@PathVariable String id) {
        List<TeamMemberEntity> memberRecords = teamMemberMapper.selectList(
                Wrappers.<TeamMemberEntity>lambdaQuery()
                        .eq(TeamMemberEntity::getTeamId, id)
                        .orderByAsc(TeamMemberEntity::getJoinedAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (TeamMemberEntity m : memberRecords) {
            UserEntity user = userMapper.selectById(m.getUserId());
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("id", m.getId());
            info.put("user_id", m.getUserId());
            info.put("role", m.getRole());
            info.put("points", m.getPoints());
            info.put("joined_at", m.getJoinedAt());
            info.put("nickname", user != null ? user.getNickname() : null);
            info.put("avatar_url", user != null ? user.getAvatarUrl() : null);
            info.put("email", user != null ? user.getEmail() : null);
            result.add(info);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/teams/{id}/disable")
    public ApiResponse<Void> disableTeam(@PathVariable String id,
                                         @RequestBody(required = false) Map<String, String> body) {
        TeamEntity team = teamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException(40404, "小队不存在");
        }
        if (!"active".equals(team.getStatus())) {
            throw new BusinessException(40001, "小队当前状态不可停用");
        }
        // US42: 停用原因必填
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(40020, "停用原因必填");
        }
        team.setStatus("disabled");
        teamMapper.updateById(team);
        // 通知小队长
        if (team.getLeaderId() != null) {
            notificationService.notify(
                    team.getLeaderId(),
                    "team_disabled",
                    "小队已停用",
                    "您的小队「" + team.getName() + "」已被管理员停用，原因：" + reason,
                    Map.of("team_id", id, "reason", reason)
            );
        }
        return ApiResponse.ok();
    }

    @PostMapping("/teams/{id}/restore")
    public ApiResponse<Void> restoreTeam(@PathVariable String id) {
        TeamEntity team = teamMapper.selectById(id);
        if (team == null) {
            throw new BusinessException(40404, "小队不存在");
        }
        team.setStatus("active");
        teamMapper.updateById(team);
        // 通知小队长
        if (team.getLeaderId() != null) {
            notificationService.notify(
                    team.getLeaderId(),
                    "team_restored",
                    "小队已恢复",
                    "您的小队「" + team.getName() + "」已被管理员恢复，所有功能已正常。",
                    Map.of("team_id", id)
            );
        }
        return ApiResponse.ok();
    }

    private Map<String, Object> userItem(UserEntity user) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", user.getId());
        item.put("email", user.getEmail());
        item.put("nickname", user.getNickname());
        item.put("avatar_url", user.getAvatarUrl());
        item.put("role", user.getRole());
        item.put("status", user.getStatus());
        item.put("credit_score", user.getCreditScore());
        item.put("created_at", user.getCreatedAt());
        return item;
    }

    private Map<String, Object> merchantItem(MerchantProfileEntity merchant) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", merchant.getId());
        item.put("user_id", merchant.getUserId());
        item.put("merchant_name", merchant.getMerchantName());
        item.put("merchant_nickname", merchant.getMerchantNickname());
        item.put("activity_domains", merchant.getActivityDomains());
        item.put("license_image_url", merchant.getLicenseImageUrl());
        item.put("audit_status", merchant.getAuditStatus());
        item.put("audit_reason", merchant.getAuditReason());
        item.put("created_at", merchant.getCreatedAt());
        UserEntity user = merchant.getUserId() == null ? null : userMapper.selectById(merchant.getUserId());
        item.put("email", user == null ? null : user.getEmail());
        return item;
    }

    private Map<String, Object> activityItem(ActivityEntity activity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", activity.getId());
        item.put("title", activity.getTitle());
        item.put("description", activity.getDescription());
        item.put("tags", activity.getTags());
        item.put("activity_type", activity.getActivityType());
        item.put("status", activity.getStatus());
        item.put("start_time", activity.getStartTime());
        item.put("end_time", activity.getEndTime());
        item.put("max_participants", activity.getMaxParticipants());
        item.put("current_participants", activity.getCurrentParticipants());
        item.put("location_name", activity.getLocationName());
        item.put("created_at", activity.getCreatedAt());
        UserEntity creator = activity.getCreatorId() == null ? null : userMapper.selectById(activity.getCreatorId());
        Map<String, Object> creatorItem = new LinkedHashMap<>();
        if (creator != null) {
            creatorItem.put("id", creator.getId());
            creatorItem.put("nickname", creator.getNickname());
            creatorItem.put("avatar_url", creator.getAvatarUrl());
        }
        item.put("creator", creator == null ? null : creatorItem);
        return item;
    }

    private Map<String, Object> activityDetailItem(ActivityEntity activity) {
        Map<String, Object> item = activityItem(activity);
        item.put("cover_image_url", activity.getCoverImageUrl());
        item.put("registration_deadline", activity.getRegistrationDeadline());
        item.put("min_credit_score", activity.getMinCreditScore());
        item.put("min_age", activity.getMinAge());
        item.put("fee_type", activity.getFeeType());
        item.put("fee_amount", activity.getFeeAmount());
        item.put("city", activity.getCity());
        item.put("location_lat", activity.getLocationLat());
        item.put("location_lng", activity.getLocationLng());
        item.put("review_reason", activity.getReviewReason());
        item.put("reviewed_by", activity.getReviewedBy());
        item.put("reviewed_at", activity.getReviewedAt());
        item.put("is_team_activity", activity.getTeamActivity());
        item.put("team_id", activity.getTeamId());
        item.put("check_in_enabled", activity.getCheckInEnabled());
        item.put("check_in_location_required", activity.getCheckInLocationRequired());
        return item;
    }

    private Map<String, Object> dashboardActivityItem(ActivityEntity activity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", activity.getId());
        item.put("title", activity.getTitle());
        item.put("status", activity.getStatus());
        item.put("created_at", activity.getCreatedAt());
        UserEntity creator = activity.getCreatorId() == null ? null : userMapper.selectById(activity.getCreatorId());
        item.put("creator", creator == null ? null : Map.of(
                "id", creator.getId(),
                "nickname", creator.getNickname() == null ? "" : creator.getNickname()
        ));
        return item;
    }

    private int clampPageSize(Integer size) {
        int value = size == null ? 10 : size;
        return Math.max(1, Math.min(value, 100));
    }

    private Map<String, Object> pageInfo(long total, int page, int pageSize) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total", total);
        map.put("page", page);
        map.put("size", pageSize);
        return map;
    }

    // region Cursor helpers — retained for /admin/teams
    private int normalizedLimit(Integer limit) {
        int value = limit == null ? 20 : limit;
        return Math.max(1, Math.min(value, 100));
    }

    private <T> List<T> trimPage(List<T> rows, int limit) {
        if (rows.size() <= limit) return rows;
        return new ArrayList<>(rows.subList(0, limit));
    }

    private <T> Map<String, Object> pagination(List<T> rows, List<T> pageRows, int limit,
                                               java.util.function.Function<T, String> cursorFactory) {
        boolean hasMore = rows.size() > limit;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("next_cursor", hasMore && !pageRows.isEmpty() ? cursorFactory.apply(pageRows.get(pageRows.size() - 1)) : null);
        map.put("has_more", hasMore);
        map.put("limit", limit);
        return map;
    }

    private String cursorOf(LocalDateTime createdAt, String id) {
        return (createdAt == null ? LocalDateTime.now() : createdAt) + "|" + id;
    }

    private void applyTeamCursor(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TeamEntity> wrapper, String cursor) {
        CursorParts parts = parseCursor(cursor);
        if (parts == null) return;
        wrapper.and(w -> w
                .lt(TeamEntity::getCreatedAt, parts.time())
                .or(w2 -> w2.eq(TeamEntity::getCreatedAt, parts.time()).lt(TeamEntity::getId, parts.id())));
    }

    private CursorParts parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank() || !cursor.contains("|")) return null;
        String[] parts = cursor.split("\\|", 2);
        if (parts.length != 2 || parts[1].isBlank()) return null;
        try {
            return new CursorParts(LocalDateTime.parse(parts[0]), parts[1]);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record CursorParts(LocalDateTime time, String id) {}
    // endregion
}
