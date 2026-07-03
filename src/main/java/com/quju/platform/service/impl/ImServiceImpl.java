package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.FriendshipEntity;
import com.quju.platform.entity.ImMessageEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.FriendshipMapper;
import com.quju.platform.mapper.ImMessageMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.ImService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ImServiceImpl implements ImService {

    private final ImMessageMapper imMessageMapper;
    private final FriendshipMapper friendshipMapper;
    private final UserMapper userMapper;

    @Override
    public ImMessageEntity send(ImMessageDto dto, String senderId) {
        // 私聊模式（entity_type = "private"）校验好友关系
        if ("private".equals(dto.getEntityType())) {
            String[] parts = dto.getEntityId().split(":");
            if (parts.length != 2) {
                throw new BusinessException(40000, "私聊entity_id格式错误，应为 userId:userId");
            }
            String otherUserId = parts[0].equals(senderId) ? parts[1] : parts[0];
            if (!otherUserId.equals(parts[0]) && !otherUserId.equals(parts[1])) {
                throw new BusinessException(40000, "私聊entity_id必须包含发送方");
            }

            // 校验是否为好友（双向 accepted）
            long friendCount = friendshipMapper.selectCount(Wrappers.<FriendshipEntity>lambdaQuery()
                    .and(w -> w.eq(FriendshipEntity::getUserId, senderId)
                            .eq(FriendshipEntity::getFriendId, otherUserId)
                            .or()
                            .eq(FriendshipEntity::getUserId, otherUserId)
                            .eq(FriendshipEntity::getFriendId, senderId))
                    .eq(FriendshipEntity::getStatus, "accepted"));
            if (friendCount == 0) {
                throw new BusinessException(40300, "不是好友关系，无法发送消息");
            }
        }

        ImMessageEntity entity = new ImMessageEntity();
        entity.setEntityType(dto.getEntityType());
        entity.setEntityId(dto.getEntityId());
        entity.setSenderId(senderId);
        entity.setType(dto.getType());
        entity.setContent(dto.getContent());
        entity.setMetadata(dto.getMetadata());
        entity.setRecalled(false);
        imMessageMapper.insert(entity);
        return entity;
    }

    @Override
    public void recall(String messageId, String userId) {
        ImMessageEntity entity = imMessageMapper.selectById(messageId);
        if (entity == null) {
            throw new BusinessException(40405, "消息不存在");
        }
        if (!entity.getSenderId().equals(userId)) {
            throw new BusinessException(40300, "只能撤回自己的消息");
        }
        if (entity.getRecalled() != null && entity.getRecalled()) {
            throw new BusinessException(40920, "消息已被撤回");
        }
        if (entity.getCreatedAt() != null &&
                Duration.between(entity.getCreatedAt(), LocalDateTime.now()).toMinutes() > 2) {
            throw new BusinessException(40920, "消息已超过可撤回时间（2分钟）");
        }
        entity.setRecalled(true);
        entity.setRecalledAt(LocalDateTime.now());
        imMessageMapper.updateById(entity);
    }

    @Override
    public CursorPage<ImMessageEntity> getMessages(String entityType, String entityId,
                                                    String cursor, int limit) {
        var query = Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, entityType)
                .eq(ImMessageEntity::getEntityId, entityId);
        if (cursor != null && !cursor.isBlank()) {
            try {
                LocalDateTime cursorTime = LocalDateTime.parse(cursor);
                query.lt(ImMessageEntity::getCreatedAt, cursorTime);
            } catch (Exception ignored) {
            }
        }
        query.orderByDesc(ImMessageEntity::getCreatedAt);
        List<ImMessageEntity> items = imMessageMapper.selectList(query.last("LIMIT " + (limit + 1)));
        return CursorPage.of(items, limit, m ->
                m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
    }

    @Override
    public List<Map<String, Object>> getConversations(String userId) {
        // 查询该用户参与的所有私聊消息，按 entity_id 分组取最新一条
        List<ImMessageEntity> allMessages = imMessageMapper.selectList(Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, "private")
                .and(w -> w.like(ImMessageEntity::getEntityId, "%" + userId + "%"))
                .orderByDesc(ImMessageEntity::getCreatedAt));

        // 按 entity_id 分组，取每组最新消息
        Map<String, ImMessageEntity> latestByConversation = new LinkedHashMap<>();
        Set<String> seenEntityIds = new HashSet<>();
        for (ImMessageEntity msg : allMessages) {
            // 只取包含当前用户的私聊
            String eid = msg.getEntityId();
            if (eid.contains(userId) && !seenEntityIds.contains(eid)) {
                seenEntityIds.add(eid);
                latestByConversation.put(eid, msg);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, ImMessageEntity> entry : latestByConversation.entrySet()) {
            String entityId = entry.getKey();
            ImMessageEntity lastMsg = entry.getValue();

            // 提取对方用户ID
            String[] parts = entityId.split(":");
            String otherUserId = parts[0].equals(userId) ? parts[1] : parts[0];

            UserEntity otherUser = userMapper.selectById(otherUserId);
            String otherNickname = otherUser != null ? otherUser.getNickname() : "未知用户";
            String otherAvatar = otherUser != null ? otherUser.getAvatarUrl() : "";

            // 未读消息数
            long unread = getUnreadCount("private", entityId, userId);

            Map<String, Object> conv = new HashMap<>();
            conv.put("entity_type", "private");
            conv.put("entity_id", entityId);
            conv.put("other_user_id", otherUserId);
            conv.put("other_nickname", otherNickname);
            conv.put("other_avatar_url", otherAvatar);
            conv.put("last_message", lastMsg.getContent());
            conv.put("last_message_type", lastMsg.getType());
            conv.put("last_message_time", lastMsg.getCreatedAt() != null ? lastMsg.getCreatedAt().toString() : "");
            conv.put("last_sender_id", lastMsg.getSenderId());
            conv.put("last_message_recalled", lastMsg.getRecalled());
            conv.put("unread_count", unread);
            result.add(conv);
        }

        // 按最后消息时间倒序
        result.sort((a, b) -> ((String) b.get("last_message_time")).compareTo((String) a.get("last_message_time")));
        return result;
    }

    @Override
    public void markRead(String entityType, String entityId, String userId) {
        // 将对方发送的未读消息标记为已读
        List<ImMessageEntity> unreadMessages = imMessageMapper.selectList(Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, entityType)
                .eq(ImMessageEntity::getEntityId, entityId)
                .eq(ImMessageEntity::getSenderId, getOtherUserId(entityId, userId))
                .isNull(ImMessageEntity::getReadAt));
        for (ImMessageEntity msg : unreadMessages) {
            msg.setReadAt(LocalDateTime.now());
            imMessageMapper.updateById(msg);
        }
    }

    @Override
    public long getUnreadCount(String entityType, String entityId, String userId) {
        return imMessageMapper.selectCount(Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, entityType)
                .eq(ImMessageEntity::getEntityId, entityId)
                .ne(ImMessageEntity::getSenderId, userId)
                .isNull(ImMessageEntity::getReadAt));
    }

    @Override
    public long getTotalUnreadCount(String userId) {
        // 查询所有私聊未读消息总数
        return imMessageMapper.selectCount(Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, "private")
                .like(ImMessageEntity::getEntityId, "%" + userId + "%")
                .ne(ImMessageEntity::getSenderId, userId)
                .isNull(ImMessageEntity::getReadAt));
    }

    /**
     * 从私聊 entity_id (格式 "userA:userB") 中提取对方用户ID
     */
    private String getOtherUserId(String entityId, String myUserId) {
        String[] parts = entityId.split(":");
        if (parts.length == 2) {
            return parts[0].equals(myUserId) ? parts[1] : parts[0];
        }
        return "";
    }
}
