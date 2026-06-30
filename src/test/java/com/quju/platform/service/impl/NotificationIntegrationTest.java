package com.quju.platform.service.impl;

import com.quju.platform.dto.common.PageResult;
import com.quju.platform.entity.NotificationEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.mapper.NotificationMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.NotificationService;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("通知服务集成测试")
class NotificationIntegrationTest {

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationMapper notificationMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userId;

    @BeforeEach
    void setUp() {
        notificationMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        UserEntity user = new UserEntity();
        user.setEmail("notif-user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Pass1234"));
        user.setNickname("NotifUser");
        user.setRole("personal");
        user.setStatus("active");
        user.setCreditScore(100);
        user.setInterestTags(List.of());
        userMapper.insert(user);
        userId = user.getId();

        // 创建多条通知
        notificationService.notify(userId, "friend_request", "好友申请", "新好友申请", Map.of("from_user_id", "someone"));
        notificationService.notify(userId, "system", "系统通知", "欢迎加入平台", Map.of());
        notificationService.notify(userId, "activity_published", "活动通过", "您的活动已发布", Map.of());
    }

    private void authenticateAs(String uid) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(uid, null,
                        List.of(new SimpleGrantedAuthority("ROLE_personal"))));
    }

    @Nested @DisplayName("通知列表")
    class ListTests {

        @Test @DisplayName("分页获取通知列表")
        void shouldListNotificationsWithPagination() {
            PageResult<NotificationEntity> result = notificationService.listPage(userId, null, null, null, 20);
            assertNotNull(result);
            assertEquals(3, result.records().size());
        }

        @Test @DisplayName("按类型筛选通知")
        void shouldFilterByType() {
            PageResult<NotificationEntity> result = notificationService.listPage(userId, "system", null, null, 20);
            assertEquals(1, result.records().size());
            assertEquals("system", result.records().get(0).getType());
        }

        @Test @DisplayName("按已读状态筛选")
        void shouldFilterByReadStatus() {
            PageResult<NotificationEntity> unread = notificationService.listPage(userId, null, false, null, 20);
            assertEquals(3, unread.records().size());

            // 标记一个为已读
            authenticateAs(userId);
            notificationService.markRead(unread.records().get(0).getId(), userId);

            PageResult<NotificationEntity> stillUnread = notificationService.listPage(userId, null, false, null, 20);
            assertEquals(2, stillUnread.records().size());
        }

        @Test @DisplayName("获取未读通知数量")
        void shouldReturnUnreadCount() {
            long unread = notificationService.unreadCount(userId);
            assertEquals(3, unread);
        }
    }

    @Nested @DisplayName("通知操作")
    class ActionTests {

        @Test @DisplayName("标记单条通知为已读")
        void shouldMarkAsRead() {
            authenticateAs(userId);
            PageResult<NotificationEntity> result = notificationService.listPage(userId, null, null, null, 20);
            String notifId = result.records().get(0).getId();

            notificationService.markRead(notifId, userId);

            NotificationEntity updated = notificationMapper.selectById(notifId);
            assertTrue(updated.getRead());
        }

        @Test @DisplayName("全部标记为已读")
        void shouldMarkAllAsRead() {
            authenticateAs(userId);
            notificationService.readAll(userId);

            PageResult<NotificationEntity> result = notificationService.listPage(userId, null, false, null, 20);
            assertEquals(0, result.records().size());

            long unread = notificationService.unreadCount(userId);
            assertEquals(0, unread);
        }
    }
}
