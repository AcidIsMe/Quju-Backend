package com.quju.platform.service.impl;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.UserMapper;
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
@DisplayName("候补名单位置查询集成测试 (US21)")
class WaitlistPositionIntegrationTest {

    @Autowired private RegistrationService registrationService;
    @Autowired private ActivityService activityService;
    @Autowired private ActivityMapper activityMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userId;
    private String activityId;

    @BeforeEach
    void setUp() {
        activityMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        UserEntity user = new UserEntity();
        user.setEmail("waitlist-user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Pass1234"));
        user.setNickname("WaitlistUser");
        user.setRole("personal");
        user.setStatus("active");
        user.setCreditScore(100);
        user.setInterestTags(List.of());
        userMapper.insert(user);
        userId = user.getId();

        authenticateAs(userId, "personal");
        ActivityCreateReq req = new ActivityCreateReq();
        req.setTitle("候补测试活动");
        req.setDescription("测试候补位置");
        req.setStartTime(LocalDateTime.now().plusDays(7));
        req.setEndTime(LocalDateTime.now().plusDays(8));
        req.setRegistrationDeadline(LocalDateTime.now().plusDays(6));
        req.setMaxParticipants(5);
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

    @Nested @DisplayName("候补位置查询")
    class PositionTests {

        @Test @DisplayName("加入候补后可以查询位置")
        void shouldGetPositionWhenInWaitlist() {
            authenticateAs(userId, "personal");
            // 填满名额
            ActivityEntity activity = activityMapper.selectById(activityId);
            activity.setCurrentParticipants(activity.getMaxParticipants());
            activityMapper.updateById(activity);

            registrationService.joinWaitlist(activityId, userId);

            Map<String, Object> position = registrationService.getWaitlistPosition(activityId, userId);
            assertNotNull(position);
            assertTrue((Boolean) position.get("in_queue"));
            assertNotNull(position.get("position"));
            assertNotNull(position.get("waiting_count_ahead"));
        }

        @Test @DisplayName("不在候补中时返回空状态")
        void shouldReturnNotInQueueWhenNotInWaitlist() {
            authenticateAs(userId, "personal");
            Map<String, Object> position = registrationService.getWaitlistPosition(activityId, userId);
            assertNotNull(position);
            assertFalse((Boolean) position.get("in_queue"));
        }
    }
}
