package com.quju.platform.service.impl;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.entity.WaitlistEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.mapper.WaitlistMapper;
import com.quju.platform.service.ActivityService;
import com.quju.platform.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("报名服务集成测试")
class RegistrationIntegrationTest {

    @Autowired private RegistrationService registrationService;
    @Autowired private ActivityService activityService;
    @Autowired private ActivityMapper activityMapper;
    @Autowired private RegistrationMapper registrationMapper;
    @Autowired private WaitlistMapper waitlistMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userId;
    private String activityId;

    @BeforeEach
    void setUp() {
        registrationMapper.delete(null);
        waitlistMapper.delete(null);
        activityMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        // 创建普通用户
        UserEntity user = new UserEntity();
        user.setEmail("reg-user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Pass1234"));
        user.setNickname("RegUser");
        user.setRole("personal");
        user.setStatus("active");
        user.setCreditScore(100);
        user.setInterestTags(List.of());
        userMapper.insert(user);
        userId = user.getId();

        // 创建已发布活动
        authenticateAs(userId, "personal");
        ActivityCreateReq req = new ActivityCreateReq();
        req.setTitle("报名测试活动");
        req.setDescription("报名测试活动描述");
        req.setStartTime(LocalDateTime.now().plusDays(7));
        req.setEndTime(LocalDateTime.now().plusDays(8));
        req.setRegistrationDeadline(LocalDateTime.now().plusDays(6));
        req.setMaxParticipants(10);
        req.setFeeType("free");
        req.setCity("测试城市");
        req.setLocationName("测试地点");
        req.setLocationLat(new BigDecimal("39.9042"));
        req.setLocationLng(new BigDecimal("116.4074"));
        ActivityEntity created = activityService.create(req, userId);
        created.setStatus("published");
        activityMapper.updateById(created);
        activityId = created.getId();
    }

    private void authenticateAs(String uid, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(uid, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Nested @DisplayName("报名流程")
    class RegisterTests {

        @Test @DisplayName("成功报名已发布活动")
        void shouldRegisterSuccessfully() {
            authenticateAs(userId, "personal");
            Map<String, Object> result = registrationService.register(activityId, userId, Map.of());
            assertNotNull(result.get("registration_id"));
            assertEquals("registered", result.get("status"));

            ActivityEntity activity = activityMapper.selectById(activityId);
            assertEquals(1, activity.getCurrentParticipants());
        }

        @Test @DisplayName("重复报名应抛异常")
        void shouldRejectDuplicateRegistration() {
            authenticateAs(userId, "personal");
            registrationService.register(activityId, userId, Map.of());
            assertThrows(BusinessException.class,
                    () -> registrationService.register(activityId, userId, Map.of()));
        }

        @Test @DisplayName("报名已满员的活动应抛异常")
        void shouldRejectWhenActivityIsFull() {
            authenticateAs(userId, "personal");
            // 直接设置参与者已满
            ActivityEntity activity = activityMapper.selectById(activityId);
            activity.setCurrentParticipants(activity.getMaxParticipants());
            activityMapper.updateById(activity);

            // 创建另一个用户
            UserEntity other = new UserEntity();
            other.setEmail("other-user@example.com");
            other.setPasswordHash(passwordEncoder.encode("Pass1234"));
            other.setNickname("OtherUser");
            other.setRole("personal");
            other.setStatus("active");
            other.setCreditScore(100);
            other.setInterestTags(List.of());
            userMapper.insert(other);

            assertThrows(BusinessException.class,
                    () -> registrationService.register(activityId, other.getId(), Map.of()));
        }

        @Test @DisplayName("报名提交自定义表单数据")
        void shouldRegisterWithFormData() {
            authenticateAs(userId, "personal");
            Map<String, Object> formData = Map.of("phone", "13800138000", "remark", "期待参加");
            Map<String, Object> result = registrationService.register(activityId, userId, formData);
            assertNotNull(result.get("registration_id"));

            RegistrationEntity registration = registrationMapper.selectList(null).get(0);
            assertNotNull(registration.getFormData());
            assertEquals("13800138000", registration.getFormData().get("phone"));
        }

        @Test @DisplayName("未发布的活动不能报名")
        void shouldRejectRegistrationOnNonPublishedActivity() {
            ActivityCreateReq req = new ActivityCreateReq();
            req.setTitle("草稿活动");
            req.setDescription("草稿活动描述");
            req.setStartTime(LocalDateTime.now().plusDays(7));
            req.setEndTime(LocalDateTime.now().plusDays(8));
            req.setRegistrationDeadline(LocalDateTime.now().plusDays(6));
            req.setMaxParticipants(10);
            req.setFeeType("free");
            req.setCity("测试城市");
            req.setLocationName("测试地点");
            req.setLocationLat(new BigDecimal("39.9042"));
            req.setLocationLng(new BigDecimal("116.4074"));
            ActivityEntity draft = activityService.create(req, userId);

            UserEntity other = new UserEntity();
            other.setEmail("draft-reg@example.com");
            other.setPasswordHash(passwordEncoder.encode("Pass1234"));
            other.setNickname("DraftRegUser");
            other.setRole("personal");
            other.setStatus("active");
            other.setCreditScore(100);
            other.setInterestTags(List.of());
            userMapper.insert(other);

            assertThrows(BusinessException.class,
                    () -> registrationService.register(draft.getId(), other.getId(), Map.of()));
        }
    }

    @Nested @DisplayName("取消报名")
    class CancelTests {

        @Test @DisplayName("报名后可以成功取消")
        void shouldCancelSuccessfully() {
            authenticateAs(userId, "personal");
            registrationService.register(activityId, userId, Map.of());
            registrationService.cancel(activityId, userId);

            ActivityEntity activity = activityMapper.selectById(activityId);
            assertEquals(0, activity.getCurrentParticipants());

            RegistrationEntity registration = registrationMapper.selectList(null).get(0);
            assertEquals("cancelled", registration.getStatus());
            assertNotNull(registration.getCancelledAt());
        }

        @Test @DisplayName("未报名就取消应抛异常")
        void shouldRejectCancelWhenNotRegistered() {
            authenticateAs(userId, "personal");
            assertThrows(BusinessException.class,
                    () -> registrationService.cancel(activityId, userId));
        }
    }

    @Nested @DisplayName("候补名单")
    class WaitlistTests {

        @Test @DisplayName("报名满员后可加入候补名单")
        void shouldJoinWaitlistWhenFull() {
            authenticateAs(userId, "personal");
            ActivityEntity activity = activityMapper.selectById(activityId);
            activity.setCurrentParticipants(activity.getMaxParticipants());
            activityMapper.updateById(activity);

            WaitlistEntity waitlist = registrationService.joinWaitlist(activityId, userId);
            assertNotNull(waitlist.getId());
            assertEquals("waiting", waitlist.getStatus());
            assertEquals(1, waitlist.getPosition());
        }

        @Test @DisplayName("重复加入候补名单应抛异常")
        void shouldRejectDuplicateWaitlist() {
            authenticateAs(userId, "personal");
            ActivityEntity activity = activityMapper.selectById(activityId);
            activity.setCurrentParticipants(activity.getMaxParticipants());
            activityMapper.updateById(activity);

            registrationService.joinWaitlist(activityId, userId);
            assertThrows(BusinessException.class,
                    () -> registrationService.joinWaitlist(activityId, userId));
        }

        @Test @DisplayName("可以主动退出候补名单")
        void shouldLeaveWaitlistSuccessfully() {
            authenticateAs(userId, "personal");
            ActivityEntity activity = activityMapper.selectById(activityId);
            activity.setCurrentParticipants(activity.getMaxParticipants());
            activityMapper.updateById(activity);

            registrationService.joinWaitlist(activityId, userId);
            registrationService.leaveWaitlist(activityId, userId);

            List<WaitlistEntity> remaining = waitlistMapper.selectList(null);
            assertTrue(remaining.isEmpty());
        }
    }
}
