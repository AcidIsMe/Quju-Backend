package com.quju.platform.service.impl;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.SummaryImageMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.ActivityService;
import com.quju.platform.service.SummaryService;
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
@DisplayName("活动总结服务集成测试 (US24)")
class SummaryIntegrationTest {

    @Autowired private SummaryService summaryService;
    @Autowired private ActivityService activityService;
    @Autowired private ActivityMapper activityMapper;
    @Autowired private SummaryImageMapper summaryImageMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userId;
    private String otherUserId;
    private String activityId;

    @BeforeEach
    void setUp() {
        summaryImageMapper.delete(null);
        activityMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        // 创建活动创建者
        UserEntity user = new UserEntity();
        user.setEmail("summary-creator@example.com");
        user.setPasswordHash(passwordEncoder.encode("Pass1234"));
        user.setNickname("SummaryCreator");
        user.setRole("personal");
        user.setStatus("active");
        user.setCreditScore(100);
        user.setInterestTags(List.of());
        userMapper.insert(user);
        userId = user.getId();

        // 创建另一个用户
        UserEntity other = new UserEntity();
        other.setEmail("other-summary@example.com");
        other.setPasswordHash(passwordEncoder.encode("Pass1234"));
        other.setNickname("OtherSummary");
        other.setRole("personal");
        other.setStatus("active");
        other.setCreditScore(100);
        other.setInterestTags(List.of());
        userMapper.insert(other);
        otherUserId = other.getId();

        // 创建已结束的活动
        authenticateAs(userId, "personal");
        ActivityCreateReq req = new ActivityCreateReq();
        req.setTitle("总结测试活动");
        req.setDescription("测试活动总结");
        req.setStartTime(LocalDateTime.now().minusDays(3));
        req.setEndTime(LocalDateTime.now().minusDays(2));
        req.setRegistrationDeadline(LocalDateTime.now().minusDays(4));
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

    @Nested @DisplayName("创建总结")
    class CreateTests {

        @Test @DisplayName("活动发起人成功创建图文总结")
        void shouldCreateSummaryAsCreator() {
            authenticateAs(userId, "personal");
            List<Map<String, Object>> images = List.of(
                    Map.of("image_url", "https://example.com/photo1.jpg", "category", "group_photo", "sort_order", 0),
                    Map.of("image_url", "https://example.com/photo2.jpg", "category", "venue", "sort_order", 1)
            );
            Map<String, Object> result = summaryService.create(activityId, userId, "本次活动圆满成功", images);
            assertNotNull(result.get("id"));
            assertEquals(activityId, result.get("activity_id"));
            assertEquals("本次活动圆满成功", result.get("content"));
        }

        @Test @DisplayName("非发起人不能创建总结")
        void shouldRejectNonCreator() {
            authenticateAs(otherUserId, "personal");
            List<Map<String, Object>> images = List.of(
                    Map.of("image_url", "https://example.com/photo.jpg", "category", "group_photo", "sort_order", 0)
            );
            assertThrows(BusinessException.class,
                    () -> summaryService.create(activityId, otherUserId, "总结内容", images));
        }

        @Test @DisplayName("至少需要1张图片")
        void shouldRejectEmptyImages() {
            authenticateAs(userId, "personal");
            assertThrows(BusinessException.class,
                    () -> summaryService.create(activityId, userId, "总结内容", List.of()));
        }
    }

    @Nested @DisplayName("查询总结")
    class DetailTests {

        @Test @DisplayName("获取活动图文总结详情")
        void shouldGetSummaryDetail() {
            authenticateAs(userId, "personal");
            List<Map<String, Object>> images = List.of(
                    Map.of("image_url", "https://example.com/photo.jpg", "category", "group_photo", "sort_order", 0)
            );
            summaryService.create(activityId, userId, "圆满成功", images);

            Map<String, Object> detail = summaryService.detail(activityId);
            assertNotNull(detail);
            assertEquals(activityId, detail.get("activity_id"));
            assertNotNull(detail.get("images"));
        }

        @Test @DisplayName("无总结时抛出异常")
        void shouldThrowWhenNoSummary() {
            assertThrows(BusinessException.class,
                    () -> summaryService.detail(activityId));
        }
    }

    @Nested @DisplayName("图片分类")
    class ClassifyTests {

        @Test @DisplayName("AI图片分类返回分类结果")
        void shouldClassifyImages() {
            authenticateAs(userId, "personal");
            List<String> imageUrls = List.of(
                    "https://example.com/img1.jpg",
                    "https://example.com/img2.jpg"
            );
            Map<String, Object> result = summaryService.classifyImages(activityId, userId, imageUrls);
            assertNotNull(result.get("images"));
        }

        @Test @DisplayName("非发起人不能调用图片分类")
        void shouldRejectClassifyByNonCreator() {
            authenticateAs(otherUserId, "personal");
            assertThrows(BusinessException.class,
                    () -> summaryService.classifyImages(activityId, otherUserId, List.of("https://example.com/img.jpg")));
        }
    }

    @Nested @DisplayName("更新图片分类")
    class UpdateImageCategoryTests {

        @Test @DisplayName("非发起人不能更新图片分类")
        void shouldRejectUpdateByNonCreator() {
            authenticateAs(userId, "personal");
            List<Map<String, Object>> images = List.of(
                    Map.of("image_url", "https://example.com/photo.jpg", "category", "group_photo", "sort_order", 0)
            );
            Map<String, Object> summary = summaryService.create(activityId, userId, "总结", images);

            // 获取图片ID
            authenticateAs(otherUserId, "personal");
            assertThrows(BusinessException.class,
                    () -> summaryService.updateImageCategory(activityId, otherUserId, "some-image-id", "venue"));
        }
    }
}
