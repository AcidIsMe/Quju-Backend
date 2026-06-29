package com.quju.platform.controller.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class UserController {

    private final UserMapper userMapper;
    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;

    @GetMapping("/me")
    public ApiResponse<UserEntity> me(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.ok(userMapper.selectById(SecurityUtil.currentUserIdOr(userId)));
    }

    @SuppressWarnings("unchecked")
    @PatchMapping("/me")
    public ApiResponse<UserEntity> updateMe(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                            @RequestBody Map<String, Object> body) {
        String uid = SecurityUtil.currentUserIdOr(userId);
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
            user.setAvatarUrl(String.valueOf(body.get("avatar_url")));
        }
        if (body.containsKey("gender")) {
            user.setGender(String.valueOf(body.get("gender")));
        }
        if (body.containsKey("birthday")) {
            user.setBirthday(LocalDate.parse(String.valueOf(body.get("birthday"))));
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
                user.setInterestTags((List<String>) tagList);
            }
        }
        userMapper.updateById(user);
        return ApiResponse.ok(user);
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> publicInfo(@PathVariable String id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(40401, "用户不存在");
        }
        long activityCount = activityMapper.selectCount(Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getCreatorId, id));
        long followerCount = 0;
        long followingCount = 0;
        // 注意：follows表使用联合主键(follower_id, followed_id)，这里粗略统计
        try {
            followerCount = 0; // 简化处理，FollowMapper非BaseMapper子类
            followingCount = 0;
        } catch (Exception ignored) {
        }

        return ApiResponse.ok(Map.of(
                "id", user.getId(),
                "nickname", user.getNickname(),
                "avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(),
                "gender", user.getGender() == null ? "" : user.getGender(),
                "bio", user.getBio() == null ? "" : user.getBio(),
                "interest_tags", user.getInterestTags() == null ? List.of() : user.getInterestTags(),
                "role", user.getRole(),
                "credit_score", user.getCreditScore(),
                "created_at", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString(),
                "stats", Map.of(
                        "activity_count", activityCount,
                        "follower_count", followerCount,
                        "following_count", followingCount
                )
        ));
    }

    @GetMapping("/check-nickname")
    public ApiResponse<Map<String, Object>> checkNickname(@RequestParam String nickname) {
        long count = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getNickname, nickname));
        return ApiResponse.ok(Map.of("available", count == 0));
    }

    @GetMapping("/me/created-activities")
    public ApiResponse<List<ActivityEntity>> createdActivities(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                               @RequestParam(defaultValue = "20") Integer limit) {
        String uid = SecurityUtil.currentUserIdOr(userId);
        return ApiResponse.ok(activityMapper.selectPage(new Page<>(1, limit), Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getCreatorId, uid)
                .orderByDesc(ActivityEntity::getCreatedAt)).getRecords());
    }

    @GetMapping("/me/joined-activities")
    public ApiResponse<List<RegistrationEntity>> joinedActivities(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                                  @RequestParam(defaultValue = "20") Integer limit) {
        String uid = SecurityUtil.currentUserIdOr(userId);
        return ApiResponse.ok(registrationMapper.selectPage(new Page<>(1, limit), Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getUserId, uid)
                .ne(RegistrationEntity::getStatus, "cancelled")
                .orderByDesc(RegistrationEntity::getCreatedAt)).getRecords());
    }
}
