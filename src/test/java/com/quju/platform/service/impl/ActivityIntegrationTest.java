package com.quju.platform.service.impl;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.ActivityService;
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
@DisplayName("活动服务集成测试")
class ActivityIntegrationTest {

    @Autowired private ActivityService activityService;
    @Autowired private ActivityMapper activityMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userId;
    private String adminId;

    @BeforeEach
    void setUp() {
        activityMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        // 创建普通用户
        UserEntity user = new UserEntity();
        user.setEmail("activity-user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Pass1234"));
        user.setNickname("ActivityUser");
        user.setRole("personal");
        user.setStatus("active");
        user.setCreditScore(100);
        user.setInterestTags(List.of());
        userMapper.insert(user);
        userId = user.getId();

        // 创建管理员用户
        UserEntity admin = new UserEntity();
        admin.setEmail("activity-admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Admin1234"));
        admin.setNickname("ActivityAdmin");
        admin.setRole("admin");
        admin.setStatus("active");
        admin.setCreditScore(100);
        admin.setInterestTags(List.of());
        userMapper.insert(admin);
        adminId = admin.getId();
    }

    private void authenticateAs(String uid, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(uid, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private ActivityCreateReq createValidReq() {
        ActivityCreateReq req = new ActivityCreateReq();
        req.setTitle("测试活动");
        req.setDescription("这是一个测试活动");
        req.setStartTime(LocalDateTime.now().plusDays(7));
        req.setEndTime(LocalDateTime.now().plusDays(8));
        req.setRegistrationDeadline(LocalDateTime.now().plusDays(6));
        req.setMaxParticipants(20);
        req.setFeeType("free");
        req.setCity("测试城市");
        req.setLocationName("测试地点");
        req.setLocationLat(new BigDecimal("39.9042"));
        req.setLocationLng(new BigDecimal("116.4074"));
        return req;
    }

    @Nested @DisplayName("活动创建")
    class CreateTests {

        @Test @DisplayName("创建草稿活动成功，状态为 draft")
        void shouldCreateDraftSuccessfully() {
            authenticateAs(userId, "personal");
            ActivityCreateReq req = createValidReq();
            ActivityEntity created = activityService.create(req, userId);
            assertNotNull(created.getId());
            assertEquals("draft", created.getStatus());
            assertEquals("测试活动", created.getTitle());
            assertEquals(userId, created.getCreatorId());
        }

        @Test @DisplayName("创建活动时 maxParticipants 为最小值 1 应成功")
        void shouldCreateWithMinMaxParticipants() {
            authenticateAs(userId, "personal");
            ActivityCreateReq req = createValidReq();
            req.setMaxParticipants(1);
            ActivityEntity created = activityService.create(req, userId);
            assertNotNull(created.getId());
            assertEquals(1, created.getMaxParticipants());
        }

        @Test @DisplayName("AI 服务未配置时提交创建应转人工审核")
        void shouldFallbackToManualReviewWhenAiUnavailable() {
            authenticateAs(userId, "personal");
            ActivityCreateReq req = createValidReq();
            req.setStatus("pending_ai_review");
            ActivityEntity created = activityService.create(req, userId);
            assertEquals("pending_manual_review", created.getStatus());
            assertEquals("uncertain", created.getAiReviewResult());
            assertEquals("AI 内容安全审核不确定，转人工审核", created.getReviewReason());
            assertNotNull(created.getReviewedAt());
        }

        @Test @DisplayName("创建活动时设置所有可选字段")
        void shouldCreateWithAllOptionalFields() {
            authenticateAs(userId, "personal");
            ActivityCreateReq req = createValidReq();
            req.setTags(List.of("运动", "户外"));
            req.setActivityType("outing");
            req.setCoverImageUrl("https://example.com/cover.jpg");
            req.setMinCreditScore(60);
            req.setMinAge(18);
            req.setFeeType("paid");
            req.setFeeAmount(new BigDecimal("99.99"));
            req.setTeamActivity(true);
            ActivityEntity created = activityService.create(req, userId);
            assertNotNull(created.getId());
            assertEquals(List.of("运动", "户外"), created.getTags());
            assertEquals("outing", created.getActivityType());
            assertTrue(created.getTeamActivity());
        }

    }

    @Nested @DisplayName("活动更新")
    class UpdateTests {

        @Test @DisplayName("更新草稿活动成功")
        void shouldUpdateDraftSuccessfully() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            ActivityCreateReq updateReq = createValidReq();
            updateReq.setTitle("更新后的标题");
            updateReq.setDescription("更新后的描述");
            ActivityEntity updated = activityService.update(created.getId(), updateReq, userId);
            assertEquals("更新后的标题", updated.getTitle());
            assertEquals("更新后的描述", updated.getDescription());
        }

        @Test @DisplayName("非创建者不能更新活动")
        void shouldRejectUpdateByNonCreator() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            String otherUserId = "other-user-id";
            ActivityCreateReq updateReq = createValidReq();
            updateReq.setTitle("试图更新");
            assertThrows(BusinessException.class,
                    () -> activityService.update(created.getId(), updateReq, otherUserId));
        }

        @Test @DisplayName("已发布活动不能更新")
        void shouldRejectUpdateOnPublishedActivity() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            created.setStatus("published");
            activityMapper.updateById(created);
            ActivityCreateReq updateReq = createValidReq();
            updateReq.setTitle("试图更新已发布活动");
            assertThrows(BusinessException.class,
                    () -> activityService.update(created.getId(), updateReq, userId));
        }
    }

    @Nested @DisplayName("活动详情")
    class DetailTests {

        @Test @DisplayName("通过ID获取活动详情")
        void shouldGetDetailById() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            ActivityEntity detail = activityService.detail(created.getId());
            assertNotNull(detail);
            assertEquals(created.getId(), detail.getId());
            assertEquals("测试活动", detail.getTitle());
        }

        @Test @DisplayName("获取不存在的活动应抛异常")
        void shouldThrowWhenNotFound() {
            assertThrows(BusinessException.class, () -> activityService.detail("non-existent-id"));
        }

        @Test @DisplayName("detailWithAggregation 返回聚合信息")
        void shouldReturnDetailWithAggregation() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            Map<String, Object> detail = activityService.detailWithAggregation(created.getId(), userId);
            assertNotNull(detail);
            assertEquals(created.getId(), detail.get("id"));
            assertEquals("测试活动", detail.get("title"));
        }

        @Test @DisplayName("detailWithAggregation 包含参与者计数")
        void detailWithAggregationShouldIncludeParticipantCount() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            Map<String, Object> detail = activityService.detailWithAggregation(created.getId(), userId);
            assertNotNull(detail.get("current_participants"));
        }
    }

    @Nested @DisplayName("删除与克隆")
    class DeleteAndCloneTests {

        @Test @DisplayName("创建者可以删除草稿活动")
        void shouldDeleteDraftByCreator() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            activityService.deleteDraft(created.getId(), userId);
            assertNull(activityMapper.selectById(created.getId()));
        }

        @Test @DisplayName("非创建者不能删除活动")
        void shouldRejectDeleteByNonCreator() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            assertThrows(BusinessException.class,
                    () -> activityService.deleteDraft(created.getId(), "other-user-id"));
        }

        @Test @DisplayName("克隆已发布活动成功")
        void shouldClonePublishedActivity() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            created.setStatus("published");
            activityMapper.updateById(created);
            ActivityEntity cloned = activityService.cloneActivity(created.getId(), userId);
            assertNotNull(cloned.getId());
            assertNotEquals(created.getId(), cloned.getId());
            assertEquals("draft", cloned.getStatus());
            assertEquals(created.getId(), cloned.getClonedFromId());
        }
    }

    @Nested @DisplayName("提交审核")
    class SubmitTests {

        @Test @DisplayName("提交草稿活动进行审核")
        void shouldSubmitDraftForReview() {
            authenticateAs(userId, "personal");
            ActivityEntity created = activityService.create(createValidReq(), userId);
            ActivityEntity submitted = activityService.submitForReview(created.getId());
            assertEquals("pending_ai_review", submitted.getStatus());
        }
    }
}
