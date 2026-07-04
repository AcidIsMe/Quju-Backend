package com.quju.platform.service;

import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.ImMessageEntity;

import java.util.List;
import java.util.Map;

public interface ImService {
    ImMessageEntity send(ImMessageDto dto, String senderId);
    void recall(String messageId, String userId);

    /** 获取消息历史（游标分页，按时间倒序） */
    CursorPage<ImMessageEntity> getMessages(String entityType, String entityId, String cursor, int limit);

    /** 获取会话列表（按最后消息时间倒序） */
    List<Map<String, Object>> getConversations(String userId);

    /** 将指定会话中对方发送的消息标记为已读 */
    void markRead(String entityType, String entityId, String userId);

    /** 获取指定会话的未读消息数 */
    long getUnreadCount(String entityType, String entityId, String userId);

    /** 获取总未读消息数 */
    long getTotalUnreadCount(String userId);

    /** 转发消息到目标会话 */
    ImMessageEntity forward(String messageId, String userId, String targetEntityType, String targetEntityId);
}
