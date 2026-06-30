package com.quju.platform.service.impl;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.dto.common.PageResult;
import com.quju.platform.dto.registration.CheckInReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.ReviewMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.ActivityService;
import com.quju.platform.service.CheckInService;
import com.quju.platform.service.RegistrationService;
import com.quju.platform.service.ReviewService;
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
@DisplayName("评价服务集成测试 (US25)")
class ReviewIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private ActivityService activityService;
    @Autowired private RegistrationService registrationService;
    @Autowired private CheckInService checkInService;
    @Autowired private ActivityMapper activityMapper;
    @Autowired private RegistrationMapper registrationMapper;
    @Autowired private ReviewMapper reviewMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userId;
    private String activityId;
    private LocalDateTime pastStart;
    private LocalDateTime pastEnd;

    @BeforeEach
    void setUp() {
        reviewMapper.delete(null);
        registrationMapper.delete(null);
        activityMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        // 创建用户
        UserEntity user = new UserEntity();
        user.setEmail("review-user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Pass1234"));
        user.setNickname("ReviewUser");
        user.setRole("personal");
        user.setStatus("active");
        user.setCreditScore(100);
        user.setInterestTags(List.of());
        userMapper.insert(user);
        userId = user.getId();

        // 创建已结束的活动（用于评价测试）
        pastStart = LocalDateTime.now().minusDays(3);
        pastEnd = LocalDateTime.now().minusDays(2);
        authenticateAs(userId, "personal");
        ActivityCreateReq req = new ActivityCreateReq();
        req.setTitle("评价测试活动");
        req.setDescription("已结束的活动");
        req.setStartTime(pastStart);
        req.setEndTime(pastEnd);
        req.setRegistrationDeadline(pastStart.minusDays(1));
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

    /** 辅助方法：报名并签到当前用户（直接操作数据库，因为活动已结束） */
    private void registerAndCheckIn() {
        RegistrationEntity reg = new RegistrationEntity();
        reg.setActivityId(activityId);
        reg.setUserId(userId);
        reg.setStatus("checked_in");
        reg.setCheckedInAt(LocalDateTime.now());
        registrationMapper.insert(reg);
    }

    @Nested @DisplayName("创建评价")
    class CreateTests {

        @Test @DisplayName("成功创建评价（活动已结束、用户已签到）")
        void shouldCreateReviewSuccessfully() {
            authenticateAs(userId, "personal");
            registerAndCheckIn();
            var review = reviewService.create(activityId, userId, "组织得很好，风景优美！");
            assertNotNull(review.getId());
            assertEquals(activityId, review.getActivityId());
            assertEquals(userId, review.getUserId());
            assertEquals("组织得很好，风景优美！", review.getContent());
        }

        @Test @DisplayName("评价内容为空应抛异常")
        void shouldRejectBlankContent() {
            authenticateAs(userId, "personal");
            registerAndCheckIn();
            assertThrows(BusinessException.class,
                    () -> reviewService.create(activityId, userId, "  "));
        }

        @Test @DisplayName("活动未结束时不能评价")
        void shouldRejectWhenActivityNotEnded() {
            authenticateAs(userId, "personal");
            // 创建未结束的活动
            ActivityCreateReq req = new ActivityCreateReq();
            req.setTitle("未结束的活动");
            req.setDescription("进行中");
            req.setStartTime(LocalDateTime.now().minusDays(1));
            req.setEndTime(LocalDateTime.now().plusDays(2));
            req.setRegistrationDeadline(LocalDateTime.now().minusDays(2));
            req.setMaxParticipants(10);
            req.setFeeType("free");
            req.setCity("测试城市");
            req.setLocationName("测试地点");
            req.setLocationLat(new BigDecimal("39.9042"));
            req.setLocationLng(new BigDecimal("116.4074"));
            ActivityEntity ongoing = activityService.create(req, userId);
            ongoing.setStatus("published");
            activityMapper.updateById(ongoing);

            assertThrows(BusinessException.class,
                    () -> reviewService.create(ongoing.getId(), userId, "不错"));
        }

        @Test @DisplayName("活动结束超过7天不能评价")
        void shouldRejectWhenBeyond7Days() {
            authenticateAs(userId, "personal");
            // 创建7天前结束的活动
            ActivityCreateReq req = new ActivityCreateReq();
            req.setTitle("很早结束的活动");
            req.setDescription("超过7天");
            req.setStartTime(LocalDateTime.now().minusDays(20));
            req.setEndTime(LocalDateTime.now().minusDays(10));
            req.setRegistrationDeadline(LocalDateTime.now().minusDays(11));
            req.setMaxParticipants(10);
            req.setFeeType("free");
            req.setCity("测试城市");
            req.setLocationName("测试地点");
            req.setLocationLat(new BigDecimal("39.9042"));
            req.setLocationLng(new BigDecimal("116.4074"));
            ActivityEntity old = activityService.create(req, userId);
            old.setStatus("published");
            activityMapper.updateById(old);

            assertThrows(BusinessException.class,
                    () -> reviewService.create(old.getId(), userId, "不错"));
        }

        @Test @DisplayName("未签到的用户不能评价")
        void shouldRejectWhenNotCheckedIn() {
            authenticateAs(userId, "personal");
            // 直接插入未签到的报名记录（活动已结束，无法通过 service 报名）
            RegistrationEntity reg = new RegistrationEntity();
            reg.setActivityId(activityId);
            reg.setUserId(userId);
            reg.setStatus("registered");
            registrationMapper.insert(reg);
            // 不签到，直接评价
            assertThrows(BusinessException.class,
                    () -> reviewService.create(activityId, userId, "不错"));
        }
    }

    @Nested @DisplayName("评价列表")
    class ListTests {

        @Test @DisplayName("获取活动评价列表返回分页数据")
        void shouldListReviewsWithPagination() {
            authenticateAs(userId, "personal");
            registerAndCheckIn();
            reviewService.create(activityId, userId, "评价内容1");

            PageResult<Map<String, Object>> result = reviewService.list(activityId, null, 20);
            assertNotNull(result);
            assertFalse(result.records().isEmpty());
            assertEquals(1, result.records().size());
        }

        @Test @DisplayName("评价列表包含用户信息")
        void shouldIncludeUserInfoInReviewList() {
            authenticateAs(userId, "personal");
            registerAndCheckIn();
            reviewService.create(activityId, userId, "好的活动！");

            PageResult<Map<String, Object>> result = reviewService.list(activityId, null, 20);
            Map<String, Object> review = result.records().get(0);
            // list() 返回平铺字段：nickname, avatar_url
            assertNotNull(review.get("nickname"));
            assertEquals("ReviewUser", review.get("nickname"));
        }
    }
}
