package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.component.websocket.ImWebSocketServer;
import com.quju.platform.entity.NotificationEntity;
import com.quju.platform.mapper.NotificationMapper;
import com.quju.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final ImWebSocketServer imWebSocketServer;

    @Override
    public void notify(String userId, String type, String title, String content, Map<String, Object> metadata) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRead(false);
        notification.setMetadata(metadata);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);

        // 通过 WebSocket 实时推送给用户
        try {
            Map<String, Object> pushPayload = new LinkedHashMap<>();
            pushPayload.put("type", "new_notification");
            pushPayload.put("data", Map.of(
                    "id", notification.getId(),
                    "type", type,
                    "title", title,
                    "content", content,
                    "is_read", false,
                    "created_at", notification.getCreatedAt() != null
                            ? notification.getCreatedAt().toString() : "",
                    "metadata", metadata != null ? metadata : Map.of()
            ));
            imWebSocketServer.pushToUser(userId, pushPayload);
        } catch (Exception e) {
            log.warn("WebSocket 推送通知失败: userId={}, type={}, error={}",
                    userId, type, e.getMessage());
        }
    }

    @Override
    public List<NotificationEntity> list(String userId) {
        return notificationMapper.selectList(Wrappers.<NotificationEntity>lambdaQuery()
                .eq(NotificationEntity::getUserId, userId)
                .orderByDesc(NotificationEntity::getCreatedAt));
    }

    @Override
    public void markRead(String notificationId, String userId) {
        NotificationEntity notification = notificationMapper.selectById(notificationId);
        if (notification != null && notification.getUserId().equals(userId)) {
            notification.setRead(true);
            notificationMapper.updateById(notification);
        }
    }

    @Override
    public long unreadCount(String userId) {
        return notificationMapper.selectCount(Wrappers.<NotificationEntity>lambdaQuery()
                .eq(NotificationEntity::getUserId, userId)
                .eq(NotificationEntity::getRead, false));
    }

    @Override
    public com.quju.platform.dto.common.PageResult<NotificationEntity> listPage(
            String userId, String type, Boolean isRead, String cursor, int limit) {
        var query = Wrappers.<NotificationEntity>lambdaQuery()
                .eq(NotificationEntity::getUserId, userId)
                .eq(type != null && !type.isBlank(), NotificationEntity::getType, type)
                .eq(isRead != null, NotificationEntity::getRead, isRead);
        if (cursor != null && !cursor.isBlank()) {
            try {
                LocalDateTime cursorTime = LocalDateTime.parse(cursor);
                query.lt(NotificationEntity::getCreatedAt, cursorTime);
            } catch (Exception ignored) {}
        }
        query.orderByDesc(NotificationEntity::getCreatedAt);
        java.util.List<NotificationEntity> items = notificationMapper.selectList(query.last("LIMIT " + (limit + 1)));
        boolean hasMore = items.size() > limit;
        if (hasMore) items = items.subList(0, limit);
        String nextCursor = "";
        if (hasMore && !items.isEmpty()) {
            LocalDateTime t = items.get(items.size() - 1).getCreatedAt();
            nextCursor = t == null ? "" : t.toString();
        }
        return new com.quju.platform.dto.common.PageResult<>(items, java.util.Map.of(
                "has_more", hasMore,
                "limit", limit,
                "next_cursor", nextCursor
        ));
    }

    @Override
    public void readAll(String userId) {
        NotificationEntity template = new NotificationEntity();
        template.setRead(true);
        notificationMapper.update(template,
                Wrappers.<NotificationEntity>lambdaUpdate()
                        .eq(NotificationEntity::getUserId, userId)
                        .eq(NotificationEntity::getRead, false));
    }
}
