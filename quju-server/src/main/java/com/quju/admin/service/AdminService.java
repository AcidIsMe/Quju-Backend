package com.quju.admin.service;

import com.quju.admin.dto.BanRequest;
import com.quju.auth.service.BizException;
import com.quju.entity.UserBan;
import com.quju.repository.UserBanRepository;
import com.quju.user.entity.User;
import com.quju.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final UserBanRepository userBanRepository;

    public AdminService(UserRepository userRepository, UserBanRepository userBanRepository) {
        this.userRepository = userRepository;
        this.userBanRepository = userBanRepository;
    }

    /** 用户查询 */
    public List<Map<String, Object>> listUsers(String q, String role, String status, int cursor, int limit) {
        List<User> all = userRepository.findAll();
        List<User> filtered = all.stream()
                .filter(u -> q == null || q.isBlank()
                        || u.getEmail().contains(q) || u.getNickname().contains(q))
                .filter(u -> role == null || role.isBlank() || role.equals(u.getRole()))
                .filter(u -> status == null || status.isBlank() || status.equals(u.getStatus()))
                .collect(Collectors.toList());

        int start = cursor * limit;
        if (start >= filtered.size()) return Collections.emptyList();
        int end = Math.min(start + limit, filtered.size());

        return filtered.subList(start, end).stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("email", u.getEmail());
            m.put("nickname", u.getNickname());
            m.put("role", u.getRole());
            m.put("status", u.getStatus());
            m.put("credit_score", u.getCreditScore());
            m.put("created_at", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
            return m;
        }).collect(Collectors.toList());
    }

    /** 用户详情 */
    public Map<String, Object> getUserDetail(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(40402, "用户不存在"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", user.getId());
        m.put("email", user.getEmail());
        m.put("nickname", user.getNickname());
        m.put("avatar_url", user.getAvatarUrl());
        m.put("gender", user.getGender());
        m.put("birthday", user.getBirthday() != null ? user.getBirthday().toString() : null);
        m.put("bio", user.getBio());
        m.put("interest_tags", user.getInterestTags());
        m.put("role", user.getRole());
        m.put("status", user.getStatus());
        m.put("credit_score", user.getCreditScore());
        m.put("created_at", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);

        // 封禁记录
        m.put("bans", Collections.emptyList());
        return m;
    }

    /** 封禁用户 */
    @Transactional
    public void banUser(String userId, BanRequest req, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(40402, "用户不存在"));

        Instant expiresAt = null;
        if (req.getExpiresAt() != null && !req.getExpiresAt().isBlank()) {
            try {
                expiresAt = Instant.parse(req.getExpiresAt());
            } catch (Exception e) {
                throw new BizException(40000, "expires_at 格式不正确");
            }
        }

        UserBan ban = UserBan.builder()
                .userId(userId)
                .reason(req.getReason())
                .bannedBy(admin.getId())
                .bannedAt(Instant.now())
                .expiresAt(expiresAt)
                .isActive(true)
                .build();
        userBanRepository.save(ban);

        user.setStatus("banned");
        userRepository.save(user);
    }

    /** 解封用户 */
    @Transactional
    public void unbanUser(String userId, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(40402, "用户不存在"));

        UserBan activeBan = userBanRepository.findFirstByUserIdAndIsActiveTrueOrderByBannedAtDesc(userId)
                .orElseThrow(() -> new BizException(40402, "该用户没有活跃的封禁记录"));

        activeBan.setIsActive(false);
        activeBan.setRevokedBy(admin.getId());
        activeBan.setRevokedAt(Instant.now());
        userBanRepository.save(activeBan);

        // 如果没有其他活跃封禁，恢复状态
        if (!userBanRepository.existsByUserIdAndIsActiveTrue(userId)) {
            user.setStatus("active");
            userRepository.save(user);
        }
    }
}
