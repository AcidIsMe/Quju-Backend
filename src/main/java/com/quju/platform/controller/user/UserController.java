package com.quju.platform.controller.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.entity.FollowEntity;
import com.quju.platform.entity.FriendshipEntity;
import com.quju.platform.entity.TeamEntity;
import com.quju.platform.entity.TeamMemberEntity;
import com.quju.platform.mapper.FollowMapper;
import com.quju.platform.mapper.FriendshipMapper;
import com.quju.platform.mapper.TeamMapper;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.SocialGraphService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class UserController {

    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;
    private final FollowMapper followMapper;
    private final FriendshipMapper friendshipMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final SocialGraphService socialGraphService;

    @GetMapping("/me")
    public ApiResponse<UserEntity> me() {
        return ApiResponse.ok(userMapper.selectById(SecurityUtil.requireCurrentUserId()));
    }

    @SuppressWarnings("unchecked")
    @PatchMapping("/me")
    public ApiResponse<UserEntity> updateMe(@RequestBody Map<String, Object> body) {
        String uid = SecurityUtil.requireCurrentUserId();
        UserEntity user = userMapper.selectById(uid);
        if (user == null) {
            throw new BusinessException(40401, "用户不存在");
        }

        if (body.containsKey("nickname")) {
            String nickname = String.valueOf(body.get("nickname")).trim();
            if (nickname.isEmpty()) {
                throw new BusinessException(40002, "昵称不能为空");
            }
            // 检查昵称唯一性（排除自己）
            UserEntity existing = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                    .eq(UserEntity::getNickname, nickname)
                    .ne(UserEntity::getId, uid));
            if (existing != null) {
                throw new BusinessException(40003, "该昵称已被占用，请更换");
            }
            user.setNickname(nickname);
        }
        if (body.containsKey("avatar_url")) {
            Object val = body.get("avatar_url");
            user.setAvatarUrl(val == null ? null : String.valueOf(val));
        }
        if (body.containsKey("gender")) {
            Object val = body.get("gender");
            user.setGender(val == null ? null : String.valueOf(val));
        }
        if (body.containsKey("birthday")) {
            Object val = body.get("birthday");
            if (val == null || String.valueOf(val).isBlank()) {
                user.setBirthday(null);
            } else {
                try {
                    user.setBirthday(LocalDate.parse(String.valueOf(val)));
                } catch (Exception e) {
                    throw new BusinessException(40000, "生日日期格式无效，请使用 yyyy-MM-dd 格式");
                }
            }
        }
        if (body.containsKey("bio")) {
            String bio = String.valueOf(body.get("bio"));
            if (bio.length() > 200) {
                throw new BusinessException(40004, "个性签名不能超过 200 字");
            }
            user.setBio(bio);
        }
        if (body.containsKey("interest_tags")) {
            Object tags = body.get("interest_tags");
            if (tags instanceof List<?> tagList) {
                List<String> safeTags = tagList.stream()
                        .filter(t -> t instanceof String && !((String) t).isBlank())
                        .map(t -> (String) t)
                        .toList();
                user.setInterestTags(safeTags);
            }
        }
        userMapper.updateById(user);
        return ApiResponse.ok(user);
    }

    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam String q,
                                                         @RequestParam(defaultValue = "20") Integer limit) {
        int size = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        List<UserEntity> users = userMapper.selectList(
                Wrappers.<UserEntity>lambdaQuery()
                        .like(UserEntity::getNickname, q)
                        .eq(UserEntity::getStatus, "active")
                        .last("LIMIT " + size));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (UserEntity user : users) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", user.getId());
            item.put("nickname", user.getNickname());
            item.put("avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
            item.put("gender", user.getGender() == null ? "" : user.getGender());
            item.put("bio", user.getBio() == null ? "" : user.getBio());
            item.put("interest_tags", user.getInterestTags() == null ? List.of() : user.getInterestTags());
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> publicInfo(@PathVariable String id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(40401, "用户不存在");
        }
        long activityCount = activityMapper.selectCount(Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getCreatorId, id));
        long followerCount = followMapper.selectCount(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFollowedId, id));
        long followingCount = followMapper.selectCount(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFollowerId, id));

        // Compute friendship_status for current viewer
        String currentUserId = SecurityUtil.getCurrentUserIdOrNull();
        String friendshipStatus = null;
        String followStatus = null;
        if (currentUserId != null && !currentUserId.equals(id)) {
            // Check friendship
            long accepted = friendshipMapper.selectCount(Wrappers.<FriendshipEntity>lambdaQuery()
                    .eq(FriendshipEntity::getUserId, currentUserId)
                    .eq(FriendshipEntity::getFriendId, id)
                    .eq(FriendshipEntity::getStatus, "accepted"));
            if (accepted > 0) {
                friendshipStatus = "accepted";
            } else {
                long pending = friendshipMapper.selectCount(Wrappers.<FriendshipEntity>lambdaQuery()
                        .eq(FriendshipEntity::getUserId, currentUserId)
                        .eq(FriendshipEntity::getFriendId, id)
                        .eq(FriendshipEntity::getStatus, "pending"));
                if (pending > 0) {
                    friendshipStatus = "pending";
                }
            }

            // Check follow
            long iFollow = followMapper.selectCount(Wrappers.<FollowEntity>lambdaQuery()
                    .eq(FollowEntity::getFollowerId, currentUserId)
                    .eq(FollowEntity::getFollowedId, id));
            long theyFollow = followMapper.selectCount(Wrappers.<FollowEntity>lambdaQuery()
                    .eq(FollowEntity::getFollowerId, id)
                    .eq(FollowEntity::getFollowedId, currentUserId));
            if (iFollow > 0 && theyFollow > 0) {
                followStatus = "mutual";
            } else if (iFollow > 0) {
                followStatus = "following";
            } else if (theyFollow > 0) {
                followStatus = "followed";
            }
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
        result.put("gender", user.getGender() == null ? "" : user.getGender());
        result.put("bio", user.getBio() == null ? "" : user.getBio());
        result.put("interest_tags", user.getInterestTags() == null ? List.of() : user.getInterestTags());
        result.put("role", user.getRole());
        result.put("credit_score", user.getCreditScore());
        result.put("created_at", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString());
        result.put("friendship_status", friendshipStatus);
        result.put("follow_status", followStatus);
        result.put("stats", Map.of(
                "activity_count", activityCount,
                "follower_count", followerCount,
                "following_count", followingCount
        ));
        return ApiResponse.ok(result);
    }

    @GetMapping("/check-nickname")
    public ApiResponse<Map<String, Object>> checkNickname(@RequestParam String nickname) {
        long count = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getNickname, nickname));
        return ApiResponse.ok(Map.of("available", count == 0));
    }

    @GetMapping("/me/created-activities")
    public ApiResponse<?> createdActivities(@RequestParam(defaultValue = "20") Integer limit,
                                            @RequestParam(required = false) String cursor) {
        String uid = SecurityUtil.requireCurrentUserId();
        int size = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        var wrapper = Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getCreatorId, uid);
        applyCursorDesc(wrapper, cursor);
        wrapper.orderByDesc(ActivityEntity::getCreatedAt).orderByDesc(ActivityEntity::getId);
        List<ActivityEntity> items = activityMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        CursorPage<ActivityEntity> page = CursorPage.of(items, size, e -> e.getCreatedAt() + "|" + e.getId());
        return ApiResponse.page(page.getItems(), paginationMap(page));
    }

    @GetMapping("/me/joined-activities")
    public ApiResponse<?> joinedActivities(@RequestParam(defaultValue = "20") Integer limit,
                                           @RequestParam(required = false) String cursor) {
        String uid = SecurityUtil.requireCurrentUserId();
        int size = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        var wrapper = Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getUserId, uid)
                .ne(RegistrationEntity::getStatus, "cancelled");
        if (cursor != null && !cursor.isBlank() && cursor.contains("|")) {
            String[] parts = cursor.split("\\|", 2);
            if (parts.length == 2) {
                try {
                    LocalDateTime time = LocalDateTime.parse(parts[0]);
                    String id = parts[1];
                    wrapper.and(w -> w
                            .lt(RegistrationEntity::getCreatedAt, time)
                            .or(w2 -> w2
                                    .eq(RegistrationEntity::getCreatedAt, time)
                                    .lt(RegistrationEntity::getId, id)));
                } catch (Exception ignored) {
                }
            }
        }
        wrapper.orderByDesc(RegistrationEntity::getCreatedAt).orderByDesc(RegistrationEntity::getId);
        List<RegistrationEntity> items = registrationMapper.selectList(wrapper.last("LIMIT " + (size + 1)));
        CursorPage<RegistrationEntity> page = CursorPage.of(items, size, e -> {
            LocalDateTime t = e.getCreatedAt();
            return (t == null ? LocalDateTime.now() : t) + "|" + e.getId();
        });

        // 批量关联查询活动详情
        List<RegistrationEntity> pageItems = page.getItems();
        List<String> activityIds = pageItems.stream()
                .map(RegistrationEntity::getActivityId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, ActivityEntity> activityMap = new java.util.HashMap<>();
        if (!activityIds.isEmpty()) {
            List<ActivityEntity> activities = activityMapper.selectList(
                    Wrappers.<ActivityEntity>lambdaQuery().in(ActivityEntity::getId, activityIds));
            for (ActivityEntity a : activities) {
                activityMap.put(a.getId(), a);
            }
        }

        List<Map<String, Object>> combined = new java.util.ArrayList<>();
        for (RegistrationEntity reg : pageItems) {
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("registration_id", reg.getId());
            item.put("activity_id", reg.getActivityId());
            item.put("status", reg.getStatus());
            item.put("created_at", reg.getCreatedAt());

            ActivityEntity activity = activityMap.get(reg.getActivityId());
            if (activity != null) {
                Map<String, Object> activityInfo = new java.util.HashMap<>();
                activityInfo.put("id", activity.getId());
                activityInfo.put("title", activity.getTitle());
                activityInfo.put("description", activity.getDescription());
                activityInfo.put("activity_type", activity.getActivityType());
                activityInfo.put("cover_image_url", activity.getCoverImageUrl());
                activityInfo.put("start_time", activity.getStartTime());
                activityInfo.put("end_time", activity.getEndTime());
                activityInfo.put("location_name", activity.getLocationName());
                activityInfo.put("city", activity.getCity());
                activityInfo.put("status", activity.getStatus());
                activityInfo.put("max_participants", activity.getMaxParticipants());
                activityInfo.put("current_participants", activity.getCurrentParticipants());
                activityInfo.put("tags", activity.getTags());
                activityInfo.put("fee_type", activity.getFeeType());
                activityInfo.put("fee_amount", activity.getFeeAmount());
                item.put("activity", activityInfo);
            }
            combined.add(item);
        }

        return ApiResponse.page(combined, paginationMap(page));
    }

    @GetMapping("/me/teams")
    public ApiResponse<List<Map<String, Object>>> myTeams() {
        String uid = SecurityUtil.requireCurrentUserId();
        List<TeamMemberEntity> memberships = teamMemberMapper.selectList(
                Wrappers.<TeamMemberEntity>lambdaQuery()
                        .eq(TeamMemberEntity::getUserId, uid)
                        .orderByDesc(TeamMemberEntity::getJoinedAt));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (TeamMemberEntity m : memberships) {
            TeamEntity team = teamMapper.selectById(m.getTeamId());
            if (team == null || "dissolved".equals(team.getStatus())) {
                continue;
            }
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", team.getId());
            item.put("name", team.getName());
            item.put("description", team.getDescription());
            item.put("interest_tags", team.getInterestTags());
            item.put("join_type", team.getJoinType());
            item.put("max_members", team.getMaxMembers());
            item.put("current_members", team.getCurrentMembers());
            item.put("avatar_url", team.getAvatarUrl());
            item.put("status", team.getStatus());
            item.put("my_role", m.getRole());
            item.put("joined_at", m.getJoinedAt());
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    private Map<String, Object> paginationMap(CursorPage<?> page) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("next_cursor", page.getNextCursor());
        map.put("has_more", page.getHasMore());
        map.put("limit", page.getLimit());
        return map;
    }

    private void applyCursorDesc(LambdaQueryWrapper<ActivityEntity> wrapper, String cursor) {
        if (cursor == null || cursor.isBlank() || !cursor.contains("|")) return;
        String[] parts = cursor.split("\\|", 2);
        if (parts.length < 2) return;
        try {
            LocalDateTime time = LocalDateTime.parse(parts[0]);
            String id = parts[1];
            wrapper.and(w -> w
                    .lt(ActivityEntity::getCreatedAt, time)
                    .or(w2 -> w2
                            .eq(ActivityEntity::getCreatedAt, time)
                            .lt(ActivityEntity::getId, id)));
        } catch (Exception ignored) {
        }
    }
}
