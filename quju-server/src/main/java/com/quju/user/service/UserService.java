package com.quju.user.service;

import com.quju.auth.service.BizException;
import com.quju.common.ErrorCode;
import com.quju.entity.Registration;
import com.quju.repository.RegistrationRepository;
import com.quju.user.dto.UpdateProfileRequest;
import com.quju.user.entity.User;
import com.quju.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RegistrationRepository registrationRepository;

    public UserService(UserRepository userRepository,
                       RegistrationRepository registrationRepository) {
        this.userRepository = userRepository;
        this.registrationRepository = registrationRepository;
    }

    /** 获取当前用户信息（完整） */
    public User getMe(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }

    /** 更新当前用户资料 */
    public User updateMe(String userId, UpdateProfileRequest req) {
        User user = getMe(userId);

        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            if (!req.getNickname().equals(user.getNickname())
                    && userRepository.existsByNickname(req.getNickname())) {
                throw new BizException(ErrorCode.NICKNAME_ALREADY_TAKEN, "昵称已被占用");
            }
            user.setNickname(req.getNickname());
        }
        if (req.getAvatarUrl() != null) {
            user.setAvatarUrl(req.getAvatarUrl());
        }
        if (req.getGender() != null) {
            user.setGender(req.getGender());
        }
        if (req.getBirthday() != null) {
            try {
                user.setBirthday(LocalDate.parse(req.getBirthday()));
            } catch (Exception e) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "生日格式不正确 (yyyy-MM-dd)");
            }
        }
        if (req.getBio() != null) {
            if (req.getBio().length() > 200) {
                throw new BizException(ErrorCode.VALIDATION_FAILED, "个性签名最多200字");
            }
            user.setBio(req.getBio());
        }
        if (req.getInterestTags() != null) {
            user.setInterestTags(listToJson(req.getInterestTags()));
        }

        return userRepository.save(user);
    }

    /** 查看用户公开信息 */
    public Map<String, Object> getPublicProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar_url", user.getAvatarUrl());
        result.put("gender", user.getGender());
        result.put("bio", user.getBio());
        result.put("interest_tags", user.getInterestTags());
        result.put("role", user.getRole());
        result.put("credit_score", user.getCreditScore());
        result.put("created_at", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        result.put("friendship_status", null);  // 暂未实现
        result.put("follow_status", null);       // 暂未实现

        Map<String, Object> stats = new LinkedHashMap<>();
        long activityCount = registrationRepository
                .findByUserIdAndStatus(userId, "registered", null).size();
        stats.put("activity_count", activityCount);
        stats.put("follower_count", 0);
        stats.put("following_count", 0);
        result.put("stats", stats);

        return result;
    }

    /** 检查昵称是否可用 */
    public boolean checkNickname(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    /** 我创建的活动（后继通过 ActivityRepository 实现） */
    public List<Map<String, Object>> getCreatedActivities(String userId) {
        return Collections.emptyList();
    }

    /** 我报名的活动（后继通过 RegistrationRepository 完整实现） */
    public List<Map<String, Object>> getJoinedActivities(String userId) {
        return Collections.emptyList();
    }

    private String listToJson(List<String> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
