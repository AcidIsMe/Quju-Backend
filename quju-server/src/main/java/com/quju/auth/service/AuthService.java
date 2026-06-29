package com.quju.auth.service;

import com.quju.auth.dto.LoginRequest;
import com.quju.auth.dto.RegisterRequest;
import com.quju.common.ErrorCode;
import com.quju.entity.ActivationToken;
import com.quju.entity.LoginAttempt;
import com.quju.entity.RefreshToken;
import com.quju.repository.ActivationTokenRepository;
import com.quju.repository.LoginAttemptRepository;
import com.quju.repository.RefreshTokenRepository;
import com.quju.security.JwtUtil;
import com.quju.user.entity.User;
import com.quju.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       ActivationTokenRepository activationTokenRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginAttemptRepository loginAttemptRepository,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.jwtUtil = jwtUtil;
    }

    // ==================== 注册 ====================

    @Transactional
    public Map<String, Object> register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_REGISTERED, "该邮箱已被注册");
        }
        if (userRepository.existsByNickname(req.getNickname())) {
            throw new BizException(ErrorCode.NICKNAME_ALREADY_TAKEN, "昵称已被占用");
        }

        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(hashPassword(req.getPassword()))
                .nickname(req.getNickname())
                .role("personal")
                .status("pending_activation")
                .creditScore(100)
                .interestTags("[]")
                .build();
        user = userRepository.save(user);

        // 生成激活令牌
        String token = randomHex32();
        ActivationToken at = ActivationToken.builder()
                .userId(user.getId())
                .token(token)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        activationTokenRepository.save(at);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("email", user.getEmail());
        result.put("activation_token", token);   // 开发环境直接返回令牌
        return result;
    }

    // ==================== 登录 ====================

    public Map<String, Object> login(LoginRequest req) {
        String email = req.getEmail().toLowerCase().trim();

        // 登录锁：同一邮箱 5分钟内连续5次失败 → 锁定15分钟
        Instant fiveMinAgo = Instant.now().minus(5, ChronoUnit.MINUTES);
        List<LoginAttempt> recentAttempts = loginAttemptRepository.findByEmailAndCreatedAtAfter(email, fiveMinAgo);
        long recentFailures = recentAttempts.stream().filter(a -> !a.getSuccess()).count();
        if (recentFailures >= 5) {
            // 检查是否还在锁定期（最近一次失败在15分钟内）
            Instant fifteenMinAgo = Instant.now().minus(15, ChronoUnit.MINUTES);
            boolean anyInLockWindow = recentAttempts.stream()
                    .filter(a -> !a.getSuccess())
                    .anyMatch(a -> a.getCreatedAt().isAfter(fifteenMinAgo));
            if (anyInLockWindow) {
                throw new BizException(ErrorCode.LOGIN_LOCKED, "密码错误次数过多，请15分钟后重试");
            }
        }

        // 查找用户
        User user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null || !verifyPassword(req.getPassword(), user.getPasswordHash())) {
            loginAttemptRepository.save(LoginAttempt.builder()
                    .email(email).success(false).build());
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "邮箱或密码错误");
        }

        // 检查账户状态
        if ("pending_activation".equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_NOT_ACTIVATED, "账户未激活，请先激活邮箱");
        }
        if ("banned".equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED, "账户已被封禁");
        }

        // 登录成功
        loginAttemptRepository.save(LoginAttempt.builder()
                .email(email).success(true).build());

        return buildLoginResponse(user);
    }

    // ==================== 退出 ====================

    @Transactional
    public void logout(String refreshTokenStr) {
        RefreshToken rt = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new BizException(ErrorCode.BAD_CREDENTIALS, "无效的令牌"));
        rt.setRevokedAt(Instant.now());
        refreshTokenRepository.save(rt);
    }

    // ==================== 刷新 Token ====================

    @Transactional
    public Map<String, Object> refresh(String refreshTokenStr) {
        if (!jwtUtil.validateRefreshToken(refreshTokenStr)) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "令牌无效或已过期");
        }

        RefreshToken rt = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new BizException(ErrorCode.BAD_CREDENTIALS, "令牌不存在"));

        if (rt.getRevokedAt() != null) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "令牌已被撤销");
        }
        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "令牌已过期");
        }

        // 撤销旧 token
        rt.setRevokedAt(Instant.now());
        refreshTokenRepository.save(rt);

        // 查找用户
        String userId = jwtUtil.parseRefreshToken(refreshTokenStr).getSubject();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BizException(ErrorCode.BAD_CREDENTIALS, "用户不存在"));

        if ("banned".equals(user.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_BANNED, "账户已被封禁");
        }

        return buildLoginResponse(user);
    }

    // ==================== 激活邮箱 ====================

    @Transactional
    public void activate(String token) {
        ActivationToken at = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BizException(40005, "激活链接无效或已过期"));

        if (at.getUsedAt() != null) {
            throw new BizException(40006, "账号已激活");
        }
        if (at.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(40005, "激活链接已过期");
        }

        User user = userRepository.findById(at.getUserId())
                .orElseThrow(() -> new BizException(40005, "用户不存在"));

        user.setStatus("active");
        userRepository.save(user);

        at.setUsedAt(Instant.now());
        activationTokenRepository.save(at);
    }

    // ==================== 重发激活邮件 ====================

    @Transactional
    public void resendActivation(String email) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new BizException(40401, "该邮箱未注册"));

        if (!"pending_activation".equals(user.getStatus())) {
            throw new BizException(40006, "账号已激活");
        }

        // 标记旧 token 为已使用
        activationTokenRepository.findByUserId(user.getId()).ifPresent(old -> {
            old.setUsedAt(Instant.now());
            activationTokenRepository.save(old);
        });

        // 生成新 token
        ActivationToken at = ActivationToken.builder()
                .userId(user.getId())
                .token(randomHex32())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
        activationTokenRepository.save(at);
    }

    // ==================== 密码工具 ====================

    private String hashPassword(String password) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[16];
            random.nextBytes(salt);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes("UTF-8"));

            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }

    private boolean verifyPassword(String password, String storedHash) {
        try {
            byte[] combined = Base64.getDecoder().decode(storedHash);
            byte[] salt = new byte[16];
            byte[] expectedHash = new byte[32];
            System.arraycopy(combined, 0, salt, 0, 16);
            System.arraycopy(combined, 16, expectedHash, 0, 32);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] actualHash = md.digest(password.getBytes("UTF-8"));

            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (NoSuchAlgorithmException | java.io.UnsupportedEncodingException e) {
            throw new RuntimeException("密码验证失败", e);
        }
    }

    private String randomHex32() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== 构建登录响应 ====================

    private Map<String, Object> buildLoginResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole());
        String refreshTokenStr = jwtUtil.generateRefreshToken(user.getId());

        // 保存 refresh token
        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshTokenStr)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(rt);

        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("id", user.getId());
        userMap.put("nickname", user.getNickname());
        userMap.put("avatar_url", user.getAvatarUrl());
        userMap.put("role", user.getRole());
        userMap.put("status", user.getStatus());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshTokenStr);
        result.put("expires_in", 7200);
        result.put("user", userMap);
        return result;
    }
}
