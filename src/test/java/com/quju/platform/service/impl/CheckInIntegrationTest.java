package com.quju.platform.service.impl;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.dto.registration.CheckInReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.ActivityService;
import com.quju.platform.service.CheckInService;
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
@DisplayName("签到服务集成测试 (US22-23)")
class CheckInIntegrationTest {

    @Autowired private CheckInService checkInService;
    @Autowired private ActivityService activityService;
    @Autowired private RegistrationService registrationService;
    @Autowired private ActivityMapper activityMapper;
    @Autowired private RegistrationMapper registrationMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userId;
    private String otherUserId;
    private String activityId;

    @BeforeEach
    void setUp() {
        registrationMapper.delete(null);
        activityMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        // 创建活动发起人
        UserEntity creator = new UserEntity();
        creator.setEmail("checkin-creator@example.com");
        creator.setPasswordHash(passwordEncoder.encode("Pass1234"));
        creator.setNickname("CheckInCreator");
        creator.setRole("personal");
        creator.setStatus("active");
        creator.setCreditScore(100);
        creator.setInterestTags(List.of());
        userMapper.insert(creator);
        userId = creator.getId();

        // 创建另一个用户
        UserEntity other = new UserEntity();
        other.setEmail("checkin-other@example.com");
        other.setPasswordHash(passwordEncoder.encode("Pass1234"));
        other.setNickname("CheckInOther");
        other.setRole("personal");
        other.setStatus("active");
        other.setCreditScore(100);
        other.setInterestTags(List.of());
        userMapper.insert(other);
        otherUserId = other.getId();

        // 创建活动中状态的活动（deadline设为未来，以便通过register服务）
        authenticateAs(userId, "personal");
        ActivityCreateReq req = new ActivityCreateReq();
        req.setTitle("签到测试活动");
        req.setDescription("进行中的活动");
        req.setStartTime(LocalDateTime.now().minusDays(1));
        req.setEndTime(LocalDateTime.now().plusDays(2));
        req.setRegistrationDeadline(LocalDateTime.now().plusDays(1));
        req.setMaxParticipants(10);
        req.setFeeType("free");
        req.setCity("测试城市");
        req.setLocationName("测试地点");
        req.setLocationLat(new BigDecimal("39.9042"));
        req.setLocationLng(new BigDecimal("116.4074"));
        ActivityEntity created = activityService.create(req, userId);
        created.setStatus("published");
        created.setCheckInEnabled(true);
        activityMapper.updateById(created);
        activityId = created.getId();
    }

    private void authenticateAs(String uid, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(uid, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Nested @DisplayName("签到操作")
    class CheckInTests {

        @Test @DisplayName("已报名用户可成功签到")
        void shouldCheckInSuccessfully() {
            authenticateAs(otherUserId, "personal");
            registrationService.register(activityId, otherUserId, Map.of());
            authenticateAs(userId, "personal");
            // 创建签到二维码供签名校验
            Map<String, Object> qrResult = checkInService.qrcode(activityId, userId);
            String qrData = (String) qrResult.get("qr_data");

            authenticateAs(otherUserId, "personal");
            CheckInReq req = new CheckInReq();
            req.setQrData(qrData);
            checkInService.checkIn(activityId, otherUserId, req);

            // 验证签到状态
            var regs = registrationMapper.selectList(null);
            assertTrue(regs.stream().anyMatch(r -> r.getCheckedInAt() != null));
        }

        @Test @DisplayName("未报名的用户不能签到")
        void shouldRejectNotRegistered() {
            authenticateAs(userId, "personal");
            Map<String, Object> qrResult = checkInService.qrcode(activityId, userId);
            String qrData = (String) qrResult.get("qr_data");

            UserEntity unregistered = new UserEntity();
            unregistered.setEmail("unregistered@example.com");
            unregistered.setPasswordHash(passwordEncoder.encode("Pass1234"));
            unregistered.setNickname("Unregistered");
            unregistered.setRole("personal");
            unregistered.setStatus("active");
            unregistered.setCreditScore(100);
            unregistered.setInterestTags(List.of());
            userMapper.insert(unregistered);

            authenticateAs(unregistered.getId(), "personal");
            CheckInReq req = new CheckInReq();
            req.setQrData(qrData);
            assertThrows(BusinessException.class,
                    () -> checkInService.checkIn(activityId, unregistered.getId(), req));
        }
    }

    @Nested @DisplayName("签到名单")
    class ListTests {

        @Test @DisplayName("活动发起人可查看签到名单")
        void shouldListCheckInsByCreator() {
            authenticateAs(userId, "personal");
            Map<String, Object> result = checkInService.list(activityId, userId);
            assertNotNull(result.get("items"));
            assertNotNull(result.get("stats"));
        }

        @Test @DisplayName("签到名单包含统计信息")
        void shouldIncludeStats() {
            authenticateAs(otherUserId, "personal");
            registrationService.register(activityId, otherUserId, Map.of());

            authenticateAs(userId, "personal");
            Map<String, Object> result = checkInService.list(activityId, userId);
            @SuppressWarnings("unchecked")
            Map<String, Object> stats = (Map<String, Object>) result.get("stats");
            assertNotNull(stats.get("total_registered"));
            assertNotNull(stats.get("total_checked_in"));
            assertNotNull(stats.get("check_in_rate"));
        }
    }

    @Nested @DisplayName("签到二维码")
    class QrCodeTests {

        @Test @DisplayName("活动发起人可生成签到二维码")
        void shouldGenerateQrCodeAsCreator() {
            authenticateAs(userId, "personal");
            Map<String, Object> result = checkInService.qrcode(activityId, userId);
            assertNotNull(result.get("qr_code_url"));
            assertNotNull(result.get("qr_data"));
            assertNotNull(result.get("expires_at"));
        }

        @Test @DisplayName("非发起人生成二维码应抛异常")
        void shouldRejectQrCodeByNonCreator() {
            authenticateAs(otherUserId, "personal");
            assertThrows(BusinessException.class,
                    () -> checkInService.qrcode(activityId, otherUserId));
        }
    }
}
