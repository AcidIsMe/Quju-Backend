package com.quju.platform.service;

import com.quju.platform.entity.NotificationEntity;

import java.util.List;
import java.util.Map;

public interface NotificationService {
    /** 创建通知 */
    void notify(String userId, String type, String title, String content, Map<String, Object> metadata);

    /** 获取用户通知列表（按时间倒序） */
    List<NotificationEntity> list(String userId);

    /** 标记通知为已读 */
    void markRead(String notificationId, String userId);

    /** 获取未读通知数量 */
    long unreadCount(String userId);

    /** 分页获取通知列表（支持按类型和已读状态筛选） */
    com.quju.platform.dto.common.PageResult<NotificationEntity> listPage(String userId, String type, Boolean isRead, String cursor, int limit);

    /** 全部标记已读 */
    void readAll(String userId);
}
