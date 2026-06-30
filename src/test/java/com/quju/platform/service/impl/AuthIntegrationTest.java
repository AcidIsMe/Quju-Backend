package com.quju.platform.service.impl;

import com.quju.platform.dto.auth.LoginReq;
import com.quju.platform.dto.auth.RegisterReq;
import com.quju.platform.entity.LoginAttemptEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.LoginAttemptMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("认证服务集成测试")
class AuthIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private UserMapper userMapper;
    @Autowired private LoginAttemptMapper loginAttemptMapper;

    @BeforeEach
    void setUp() {
        loginAttemptMapper.delete(null);
        userMapper.delete(null);
    }

    @Nested @DisplayName("注册流程")
    class RegisterTests {
        @Test @DisplayName("个人注册成功并返回激活Token")
        void shouldRegisterPersonalSuccessfully() {
            RegisterReq req = new RegisterReq();
            req.setEmail("reg-test@example.com");
            req.setPassword("Abc12345");
            req.setNickname("RegUser");
            Map<String, Object> result = authService.registerPersonal(req);
            assertNotNull(result);
            assertEquals("reg-test@example.com", result.get("email"));
            assertNotNull(result.get("activation_token"));
            var users = userMapper.selectList(null);
            assertEquals(1, users.size());
            assertEquals("pending_activation", users.get(0).getStatus());
        }

        @Test @DisplayName("重复邮箱注册应抛异常")
        void shouldRejectDuplicateEmail() {
            RegisterReq req = new RegisterReq();
            req.setEmail("dup-email@example.com");
            req.setPassword("Abc12345");
            req.setNickname("FirstUser");
            authService.registerPersonal(req);
            RegisterReq duplicateReq = new RegisterReq();
            duplicateReq.setEmail("dup-email@example.com");
            duplicateReq.setPassword("Abc12345");
            duplicateReq.setNickname("OtherNick");
            assertThrows(BusinessException.class, () -> authService.registerPersonal(duplicateReq));
        }

        @Test @DisplayName("重复昵称注册应抛异常")
        void shouldRejectDuplicateNickname() {
            RegisterReq req = new RegisterReq();
            req.setEmail("first@example.com");
            req.setPassword("Abc12345");
            req.setNickname("UniqueNick");
            authService.registerPersonal(req);
            RegisterReq duplicateReq = new RegisterReq();
            duplicateReq.setEmail("second@example.com");
            duplicateReq.setPassword("Abc12345");
            duplicateReq.setNickname("UniqueNick");
            assertThrows(BusinessException.class, () -> authService.registerPersonal(duplicateReq));
        }
    }

    @Nested @DisplayName("激活流程")
    class ActivationTests {
        @Test @DisplayName("激活Token后可正常登录")
        void shouldActivateAndThenLogin() {
            RegisterReq req = new RegisterReq();
            req.setEmail("activate-test@example.com");
            req.setPassword("Abc12345");
            req.setNickname("ActivateUser");
            Map<String, Object> regResult = authService.registerPersonal(req);
            String token = (String) regResult.get("activation_token");
            authService.activate(token);
            LoginReq loginReq = new LoginReq();
            loginReq.setEmail("activate-test@example.com");
            loginReq.setPassword("Abc12345");
            Map<String, Object> loginResult = authService.login(loginReq, "127.0.0.1");
            assertNotNull(loginResult.get("access_token"));
            assertNotNull(loginResult.get("refresh_token"));
            assertNotNull(loginResult.get("expires_in"));
            assertNotNull(loginResult.get("user"));
        }

        @Test @DisplayName("未激活用户登录应返回特定错误")
        void shouldRejectLoginWhenNotActivated() {
            RegisterReq req = new RegisterReq();
            req.setEmail("not-activated@example.com");
            req.setPassword("Abc12345");
            req.setNickname("NotActiveUser");
            authService.registerPersonal(req);
            LoginReq loginReq = new LoginReq();
            loginReq.setEmail("not-activated@example.com");
            loginReq.setPassword("Abc12345");
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(loginReq, "127.0.0.1"));
            assertEquals(40102, ex.getCode());
        }
    }

    @Nested @DisplayName("登录频率限制")
    class LoginLockTests {
        @Test @DisplayName("累计5次失败记录后应锁定账户")
        void shouldLockAccountAfter5Failures() {
            RegisterReq req = new RegisterReq();
            req.setEmail("lock-test@example.com");
            req.setPassword("Abc12345");
            req.setNickname("LockUser");
            Map<String, Object> regResult = authService.registerPersonal(req);
            authService.activate((String) regResult.get("activation_token"));
            String email = "lock-test@example.com";
            for (int i = 0; i < 5; i++) {
                LoginAttemptEntity attempt = new LoginAttemptEntity();
                attempt.setEmail(email);
                attempt.setIpAddress("127.0.0.1");
                attempt.setSuccess(false);
                attempt.setCreatedAt(LocalDateTime.now());
                loginAttemptMapper.insert(attempt);
            }
            LoginReq correctReq = new LoginReq();
            correctReq.setEmail(email);
            correctReq.setPassword("Abc12345");
            BusinessException lockedEx = assertThrows(BusinessException.class,
                    () -> authService.login(correctReq, "127.0.0.1"));
            assertEquals(42901, lockedEx.getCode());
        }
    }

    @Nested @DisplayName("刷新令牌")
    class RefreshTokenTests {
        @Test @DisplayName("刷新令牌可获取新的access_token")
        void shouldRefreshTokenSuccessfully() {
            RegisterReq req = new RegisterReq();
            req.setEmail("refresh-test@example.com");
            req.setPassword("Abc12345");
            req.setNickname("RefreshUser");
            Map<String, Object> regResult = authService.registerPersonal(req);
            authService.activate((String) regResult.get("activation_token"));
            LoginReq loginReq = new LoginReq();
            loginReq.setEmail("refresh-test@example.com");
            loginReq.setPassword("Abc12345");
            Map<String, Object> loginResult = authService.login(loginReq, "127.0.0.1");
            String refreshToken = (String) loginResult.get("refresh_token");
            Map<String, Object> refreshResult = authService.refresh(refreshToken);
            assertNotNull(refreshResult.get("access_token"));
            assertNotNull(refreshResult.get("refresh_token"));
            assertNotEquals(refreshToken, refreshResult.get("refresh_token"));
        }

        @Test @DisplayName("使用已吊销的刷新令牌应抛异常")
        void shouldRejectRevokedRefreshToken() {
            RegisterReq req = new RegisterReq();
            req.setEmail("revoke-test@example.com");
            req.setPassword("Abc12345");
            req.setNickname("RevokeUser");
            Map<String, Object> regResult = authService.registerPersonal(req);
            authService.activate((String) regResult.get("activation_token"));
            LoginReq loginReq = new LoginReq();
            loginReq.setEmail("revoke-test@example.com");
            loginReq.setPassword("Abc12345");
            Map<String, Object> loginResult = authService.login(loginReq, "127.0.0.1");
            String refreshToken = (String) loginResult.get("refresh_token");
            authService.logout(refreshToken);
            assertThrows(BusinessException.class, () -> authService.refresh(refreshToken));
        }
    }
}
