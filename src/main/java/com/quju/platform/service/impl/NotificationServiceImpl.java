package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.NotificationEntity;
import com.quju.platform.mapper.NotificationMapper;
import com.quju.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void notify(String userId, String type, String title, String content, Map<String, Object> metadata) {
        NotificationEntity notification = new NotificationEntity();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRead(false);
        notification.setMetadata(metadata);
        notificationMapper.insert(notification);
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
        java.util.List<NotificationEntity> unread = notificationMapper.selectList(
                Wrappers.<NotificationEntity>lambdaQuery()
                        .eq(NotificationEntity::getUserId, userId)
                        .eq(NotificationEntity::getRead, false));
        for (NotificationEntity n : unread) {
            n.setRead(true);
            notificationMapper.updateById(n);
        }
    }
}
