package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.NotificationEntity;
import com.quju.platform.mapper.NotificationMapper;
import com.quju.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
}
