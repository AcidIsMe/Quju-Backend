package com.quju.platform.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.component.statemachine.ActivityStateMachine;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.*;
import com.quju.platform.mapper.*;
import com.quju.platform.service.MerchantService;
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
    private final TeamMapper teamMapper;
    private final ActivityStateMachine activityStateMachine;

    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> users(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String role,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(defaultValue = "20") Integer limit) {
        int size = normalizedLimit(limit);
        var wrapper = Wrappers.<UserEntity>lambdaQuery()
                .and(q != null && !q.isBlank(), w -> w.like(UserEntity::getEmail, q).or().like(UserEntity::getNickname, q))
                .eq(role != null, UserEntity::getRole, role)
                .eq(status != null, UserEntity::getStatus, status);
        applyUserCursor(wrapper, cursor);
        wrapper.orderByDesc(UserEntity::getCreatedAt).orderByDesc(UserEntity::getId);
        List<UserEntity> rows = userMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        List<UserEntity> pageRows = trimPage(rows, size);
        List<Map<String, Object>> data = pageRows.stream().map(this::userItem).toList();
        return ApiResponse.page(data, pagination(rows, pageRows, size, user -> cursorOf(user.getCreatedAt(), user.getId())));
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
    public ApiResponse<List<Map<String, Object>>> pendingMerchants(@RequestParam(required = false) String cursor,
                                                                   @RequestParam(defaultValue = "20") Integer limit) {
        int size = normalizedLimit(limit);
        var wrapper = Wrappers.<MerchantProfileEntity>lambdaQuery()
                .eq(MerchantProfileEntity::getAuditStatus, "pending");
        applyMerchantCursor(wrapper, cursor);
        wrapper.orderByDesc(MerchantProfileEntity::getCreatedAt).orderByDesc(MerchantProfileEntity::getId);
        List<MerchantProfileEntity> rows = merchantProfileMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        List<MerchantProfileEntity> pageRows = trimPage(rows, size);
        List<Map<String, Object>> data = pageRows.stream().map(this::merchantItem).toList();
        return ApiResponse.page(data, pagination(rows, pageRows, size, merchant -> cursorOf(merchant.getCreatedAt(), merchant.getId())));
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
                                                             @RequestParam(required = false) String cursor,
                                                             @RequestParam(defaultValue = "20") Integer limit) {
        int size = normalizedLimit(limit);
        var wrapper = Wrappers.<ActivityEntity>lambdaQuery()
                .eq(status != null, ActivityEntity::getStatus, status)
                .and(q != null && !q.isBlank(), w -> w.like(ActivityEntity::getTitle, q).or().like(ActivityEntity::getDescription, q));
        applyActivityCursor(wrapper, cursor);
        wrapper.orderByDesc(ActivityEntity::getCreatedAt).orderByDesc(ActivityEntity::getId);
        List<ActivityEntity> rows = activityMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        List<ActivityEntity> pageRows = trimPage(rows, size);
        List<Map<String, Object>> data = pageRows.stream().map(this::activityItem).toList();
        return ApiResponse.page(data, pagination(rows, pageRows, size, activity -> cursorOf(activity.getCreatedAt(), activity.getId())));
    }

    @PostMapping("/activities/{id}/review")
    public ApiResponse<ActivityEntity> reviewActivity(@PathVariable String id,
                                                      @RequestBody Map<String, String> body) {
        String adminId = SecurityUtil.requireCurrentUserId();
        ActivityEntity activity = activityMapper.selectById(id);
        if (activity != null) {
            String action = body.getOrDefault("action", "approve");
            activity.setStatus("approve".equals(action) ? activityStateMachine.approve() : activityStateMachine.reject());
            activity.setReviewReason(body.get("reason"));
            activity.setReviewedBy(adminId);
            activity.setReviewedAt(LocalDateTime.now());
            activityMapper.updateById(activity);
        }
        return ApiResponse.ok(activity);
    }

    @PostMapping("/activities/{id}/take-down")
    public ApiResponse<Void> takeDown(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        ActivityEntity activity = activityMapper.selectById(id);
        if (activity != null) {
            activity.setStatus(activityStateMachine.takeDown());
            activity.setReviewReason(body == null ? null : body.get("reason"));
            activityMapper.updateById(activity);
        }
        return ApiResponse.ok();
    }

    @PostMapping("/activities/{id}/restore")
    public ApiResponse<Void> restoreActivity(@PathVariable String id) {
        ActivityEntity activity = activityMapper.selectById(id);
        if (activity != null) {
            activity.setStatus("published");
            activityMapper.updateById(activity);
        }
        return ApiResponse.ok();
    }

    @GetMapping("/teams")
    public ApiResponse<List<TeamEntity>> teams(@RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "20") Integer limit) {
        int size = normalizedLimit(limit);
        var wrapper = Wrappers.<TeamEntity>lambdaQuery();
        applyTeamCursor(wrapper, cursor);
        wrapper.orderByDesc(TeamEntity::getCreatedAt).orderByDesc(TeamEntity::getId);
        List<TeamEntity> rows = teamMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        List<TeamEntity> pageRows = trimPage(rows, size);
        return ApiResponse.page(pageRows, pagination(rows, pageRows, size, team -> cursorOf(team.getCreatedAt(), team.getId())));
    }

    @GetMapping("/teams/{id}")
    public ApiResponse<TeamEntity> team(@PathVariable String id) {
        return ApiResponse.ok(teamMapper.selectById(id));
    }

    @PostMapping("/teams/{id}/disable")
    public ApiResponse<Void> disableTeam(@PathVariable String id) {
        TeamEntity team = teamMapper.selectById(id);
        if (team != null) {
            team.setStatus("disabled");
            teamMapper.updateById(team);
        }
        return ApiResponse.ok();
    }

    @PostMapping("/teams/{id}/restore")
    public ApiResponse<Void> restoreTeam(@PathVariable String id) {
        TeamEntity team = teamMapper.selectById(id);
        if (team != null) {
            team.setStatus("active");
            teamMapper.updateById(team);
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

    private int normalizedLimit(Integer limit) {
        int value = limit == null ? 20 : limit;
        return Math.max(1, Math.min(value, 100));
    }

    private <T> List<T> trimPage(List<T> rows, int limit) {
        if (rows.size() <= limit) {
            return rows;
        }
        return new ArrayList<>(rows.subList(0, limit));
    }

    private <T> Map<String, Object> pagination(List<T> rows,
                                               List<T> pageRows,
                                               int limit,
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

    private void applyUserCursor(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity> wrapper, String cursor) {
        CursorParts parts = parseCursor(cursor);
        if (parts == null) return;
        wrapper.and(w -> w
                .lt(UserEntity::getCreatedAt, parts.time())
                .or(w2 -> w2.eq(UserEntity::getCreatedAt, parts.time()).lt(UserEntity::getId, parts.id())));
    }

    private void applyMerchantCursor(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantProfileEntity> wrapper, String cursor) {
        CursorParts parts = parseCursor(cursor);
        if (parts == null) return;
        wrapper.and(w -> w
                .lt(MerchantProfileEntity::getCreatedAt, parts.time())
                .or(w2 -> w2.eq(MerchantProfileEntity::getCreatedAt, parts.time()).lt(MerchantProfileEntity::getId, parts.id())));
    }

    private void applyActivityCursor(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ActivityEntity> wrapper, String cursor) {
        CursorParts parts = parseCursor(cursor);
        if (parts == null) return;
        wrapper.and(w -> w
                .lt(ActivityEntity::getCreatedAt, parts.time())
                .or(w2 -> w2.eq(ActivityEntity::getCreatedAt, parts.time()).lt(ActivityEntity::getId, parts.id())));
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

    private record CursorParts(LocalDateTime time, String id) {
    }
}
