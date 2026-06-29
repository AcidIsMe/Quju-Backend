package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.auth.LoginReq;
import com.quju.platform.dto.auth.MerchantApplyReq;
import com.quju.platform.dto.auth.RegisterReq;
import com.quju.platform.entity.ActivationTokenEntity;
import com.quju.platform.entity.LoginAttemptEntity;
import com.quju.platform.entity.MerchantProfileEntity;
import com.quju.platform.entity.RefreshTokenEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivationTokenMapper;
import com.quju.platform.mapper.LoginAttemptMapper;
import com.quju.platform.mapper.MerchantProfileMapper;
import com.quju.platform.mapper.RefreshTokenMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.AuthService;
import com.quju.platform.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final MerchantProfileMapper merchantProfileMapper;
    private final ActivationTokenMapper activationTokenMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final LoginAttemptMapper loginAttemptMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    @Transactional
    public Map<String, Object> registerPersonal(RegisterReq req) {
        UserEntity user = createUser(req.getEmail(), req.getPassword(), req.getNickname(), "personal");
        String token = createActivationToken(user.getId());
        return Map.of("email", user.getEmail(), "activation_token", token);
    }

    @Override
    @Transactional
    public Map<String, Object> registerMerchant(MerchantApplyReq req) {
        UserEntity user = createUser(req.getEmail(), req.getPassword(), req.getNickname(), "merchant");
        MerchantProfileEntity profile = new MerchantProfileEntity();
        profile.setUserId(user.getId());
        profile.setMerchantName(req.getMerchantName());
        profile.setMerchantNickname(req.getNickname());
        profile.setActivityDomains(req.getActivityDomains() == null ? List.of() : req.getActivityDomains());
        profile.setLicenseImageUrl(req.getLicenseImageUrl());
        profile.setAuditStatus("pending");
        merchantProfileMapper.insert(profile);
        String token = createActivationToken(user.getId());
        return Map.of("email", user.getEmail(), "audit_status", "pending", "activation_token", token);
    }

    @Override
    @Transactional
    public Map<String, Object> login(LoginReq req, String ipAddress) {
        // 登录频率限制：15分钟内连续5次失败 → 锁定15分钟
        LocalDateTime lockWindow = LocalDateTime.now().minusMinutes(15);
        long recentFailures = loginAttemptMapper.selectList(Wrappers.<LoginAttemptEntity>lambdaQuery()
                .eq(LoginAttemptEntity::getEmail, req.getEmail())
                .eq(LoginAttemptEntity::getSuccess, false)
                .ge(LoginAttemptEntity::getCreatedAt, lockWindow))
                .size();
        if (recentFailures >= 5) {
            throw new BusinessException(42901, "密码错误次数过多，请 15 分钟后重试");
        }

        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getEmail, req.getEmail()));
        boolean success = user != null && passwordEncoder.matches(req.getPassword(), user.getPasswordHash());
        LoginAttemptEntity attempt = new LoginAttemptEntity();
        attempt.setEmail(req.getEmail());
        attempt.setIpAddress(ipAddress);
        attempt.setSuccess(success);
        loginAttemptMapper.insert(attempt);

        if (!success) {
            throw new BusinessException(40101, "邮箱或密码错误");
        }
        if ("pending_activation".equals(user.getStatus())) {
            throw new BusinessException(40102, "账户尚未激活，请先完成邮箱激活");
        }
        if ("banned".equals(user.getStatus())) {
            throw new BusinessException(40103, "账户已被封禁，如有疑问请联系平台");
        }

        String accessToken = jwtTokenUtil.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenUtil.createRefreshToken(user.getId());
        RefreshTokenEntity refresh = new RefreshTokenEntity();
        refresh.setUserId(user.getId());
        refresh.setToken(refreshToken);
        refresh.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenUtil.getRefreshTokenTtlSeconds()));
        refreshTokenMapper.insert(refresh);

        return Map.of(
                "access_token", accessToken,
                "refresh_token", refreshToken,
                "expires_in", jwtTokenUtil.getAccessTokenTtlSeconds(),
                "user", Map.of(
                        "id", user.getId(),
                        "nickname", user.getNickname(),
                        "avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl(),
                        "role", user.getRole(),
                        "status", user.getStatus()
                )
        );
    }

    @Override
    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getToken, refreshToken)
                .isNull(RefreshTokenEntity::getRevokedAt));
        if (stored == null || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40104, "refresh token 无效或已过期");
        }
        UserEntity user = userMapper.selectById(stored.getUserId());
        String newAccessToken = jwtTokenUtil.createAccessToken(user.getId(), user.getRole());
        String newRefreshToken = jwtTokenUtil.createRefreshToken(user.getId());
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenMapper.updateById(stored);
        RefreshTokenEntity refresh = new RefreshTokenEntity();
        refresh.setUserId(user.getId());
        refresh.setToken(newRefreshToken);
        refresh.setExpiresAt(LocalDateTime.now().plusSeconds(jwtTokenUtil.getRefreshTokenTtlSeconds()));
        refreshTokenMapper.insert(refresh);
        return Map.of("access_token", newAccessToken, "refresh_token", newRefreshToken, "expires_in", jwtTokenUtil.getAccessTokenTtlSeconds());
    }

    @Override
    @Transactional
    public void activate(String token) {
        ActivationTokenEntity activation = activationTokenMapper.selectOne(Wrappers.<ActivationTokenEntity>lambdaQuery()
                .eq(ActivationTokenEntity::getToken, token));
        if (activation == null || activation.getUsedAt() != null || activation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40005, "激活链接无效或已过期");
        }
        UserEntity user = userMapper.selectById(activation.getUserId());
        user.setStatus("active");
        userMapper.updateById(user);
        activation.setUsedAt(LocalDateTime.now());
        activationTokenMapper.updateById(activation);
    }

    @Override
    public void resendActivation(String email) {
        UserEntity user = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getEmail, email));
        if (user != null && "pending_activation".equals(user.getStatus())) {
            // 将旧的激活Token标记为已使用
            activationTokenMapper.selectList(Wrappers.<ActivationTokenEntity>lambdaQuery()
                            .eq(ActivationTokenEntity::getUserId, user.getId())
                            .isNull(ActivationTokenEntity::getUsedAt))
                    .forEach(t -> {
                        t.setUsedAt(LocalDateTime.now());
                        activationTokenMapper.updateById(t);
                    });
            createActivationToken(user.getId());
        }
    }

    @Override
    public void logout(String refreshToken) {
        RefreshTokenEntity stored = refreshTokenMapper.selectOne(Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getToken, refreshToken)
                .isNull(RefreshTokenEntity::getRevokedAt));
        if (stored != null) {
            stored.setRevokedAt(LocalDateTime.now());
            refreshTokenMapper.updateById(stored);
        }
    }

    private UserEntity createUser(String email, String password, String nickname, String role) {
        if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getEmail, email)) > 0) {
            throw new BusinessException(40001, "该邮箱已被注册");
        }
        if (userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getNickname, nickname)) > 0) {
            throw new BusinessException(40003, "昵称已被占用");
        }
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus("pending_activation");
        user.setCreditScore(100);
        user.setInterestTags(List.of());
        userMapper.insert(user);
        return user;
    }

    private String createActivationToken(String userId) {
        ActivationTokenEntity activation = new ActivationTokenEntity();
        String token = UUID.randomUUID().toString().replace("-", "");
        activation.setUserId(userId);
        activation.setToken(token);
        activation.setExpiresAt(LocalDateTime.now().plusHours(24));
        activationTokenMapper.insert(activation);
        return token;
    }
}
