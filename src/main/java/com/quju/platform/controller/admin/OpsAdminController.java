package com.quju.platform.controller.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quju.platform.component.statemachine.ActivityStateMachine;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.*;
import com.quju.platform.mapper.*;
import com.quju.platform.service.MerchantService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    public ApiResponse<List<UserEntity>> users(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String role,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(defaultValue = "20") Integer limit) {
        return ApiResponse.ok(userMapper.selectPage(new Page<>(1, limit), Wrappers.<UserEntity>lambdaQuery()
                .and(q != null && !q.isBlank(), w -> w.like(UserEntity::getEmail, q).or().like(UserEntity::getNickname, q))
                .eq(role != null, UserEntity::getRole, role)
                .eq(status != null, UserEntity::getStatus, status)
                .orderByDesc(UserEntity::getCreatedAt)).getRecords());
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserEntity> user(@PathVariable String id) {
        return ApiResponse.ok(userMapper.selectById(id));
    }

    @PostMapping("/users/{id}/ban")
    public ApiResponse<Void> ban(@PathVariable String id,
                                 @RequestHeader(value = "X-User-Id", required = false) String adminId,
                                 @RequestBody Map<String, String> body) {
        UserBanEntity ban = new UserBanEntity();
        ban.setUserId(id);
        ban.setReason(body.getOrDefault("reason", "违规"));
        ban.setBannedBy(SecurityUtil.currentUserIdOr(adminId == null ? "admin" : adminId));
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
    public ApiResponse<Void> unban(@PathVariable String id,
                                   @RequestHeader(value = "X-User-Id", required = false) String adminId) {
        userBanMapper.selectList(Wrappers.<UserBanEntity>lambdaQuery()
                        .eq(UserBanEntity::getUserId, id)
                        .eq(UserBanEntity::getActive, true))
                .forEach(ban -> {
                    ban.setActive(false);
                    ban.setRevokedBy(SecurityUtil.currentUserIdOr(adminId == null ? "admin" : adminId));
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
    public ApiResponse<List<MerchantProfileEntity>> pendingMerchants() {
        return ApiResponse.ok(merchantProfileMapper.selectList(Wrappers.<MerchantProfileEntity>lambdaQuery()
                .eq(MerchantProfileEntity::getAuditStatus, "pending")));
    }

    @PostMapping("/merchants/{id}/approve")
    public ApiResponse<Void> approveMerchant(@PathVariable String id,
                                             @RequestHeader(value = "X-User-Id", required = false) String adminId) {
        merchantService.approve(id, SecurityUtil.currentUserIdOr(adminId == null ? "admin" : adminId));
        return ApiResponse.ok();
    }

    @PostMapping("/merchants/{id}/reject")
    public ApiResponse<Void> rejectMerchant(@PathVariable String id,
                                            @RequestHeader(value = "X-User-Id", required = false) String adminId,
                                            @RequestBody Map<String, String> body) {
        merchantService.reject(id, body.get("reason"), SecurityUtil.currentUserIdOr(adminId == null ? "admin" : adminId));
        return ApiResponse.ok();
    }

    @GetMapping("/activities")
    public ApiResponse<List<ActivityEntity>> activities(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String q,
                                                        @RequestParam(defaultValue = "20") Integer limit) {
        return ApiResponse.ok(activityMapper.selectPage(new Page<>(1, limit), Wrappers.<ActivityEntity>lambdaQuery()
                .eq(status != null, ActivityEntity::getStatus, status)
                .and(q != null && !q.isBlank(), w -> w.like(ActivityEntity::getTitle, q).or().like(ActivityEntity::getDescription, q))
                .orderByDesc(ActivityEntity::getCreatedAt)).getRecords());
    }

    @PostMapping("/activities/{id}/review")
    public ApiResponse<ActivityEntity> reviewActivity(@PathVariable String id,
                                                      @RequestHeader(value = "X-User-Id", required = false) String adminId,
                                                      @RequestBody Map<String, String> body) {
        ActivityEntity activity = activityMapper.selectById(id);
        if (activity != null) {
            String action = body.getOrDefault("action", "approve");
            activity.setStatus("approve".equals(action) ? activityStateMachine.approve() : activityStateMachine.reject());
            activity.setReviewReason(body.get("reason"));
            activity.setReviewedBy(SecurityUtil.currentUserIdOr(adminId == null ? "admin" : adminId));
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
    public ApiResponse<List<TeamEntity>> teams() {
        return ApiResponse.ok(teamMapper.selectList(Wrappers.<TeamEntity>lambdaQuery().orderByDesc(TeamEntity::getCreatedAt)));
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
}
