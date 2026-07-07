package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.platform.component.websocket.SessionManager;
import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.*;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.*;
import com.quju.platform.service.ImService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ImServiceImpl implements ImService {

    private static final Logger log = LoggerFactory.getLogger(ImServiceImpl.class);

    private final ImMessageMapper imMessageMapper;
    private final FriendshipMapper friendshipMapper;
    private final UserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final GroupChatReadMarkerMapper groupChatReadMarkerMapper;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public ImMessageEntity send(ImMessageDto dto, String senderId) {
        // 私聊模式校验好友关系
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

        // 群聊模式校验群成员身份
        if ("group".equals(dto.getEntityType())) {
            String groupId = dto.getEntityId().startsWith("team:")
                    ? dto.getEntityId().substring(5) : dto.getEntityId();
            Long membershipCount = teamMemberMapper.selectCount(Wrappers.<TeamMemberEntity>lambdaQuery()
                    .eq(TeamMemberEntity::getTeamId, groupId)
                    .eq(TeamMemberEntity::getUserId, senderId));
            if (membershipCount == 0) {
                throw new BusinessException(40300, "您不是该群聊成员，无法发送消息");
            }
        }

        // 保存消息
        ImMessageEntity entity = new ImMessageEntity();
        entity.setEntityType(dto.getEntityType());
        entity.setEntityId(dto.getEntityId());
        entity.setSenderId(senderId);
        entity.setType(dto.getType());
        entity.setContent(dto.getContent());
        entity.setMetadata(dto.getMetadata());
        entity.setRecalled(false);
        imMessageMapper.insert(entity);

        // 构建广播消息
        Map<String, Object> broadcast = new LinkedHashMap<>();
        broadcast.put("type", "new_message");
        broadcast.put("message_id", entity.getId());
        broadcast.put("entity_type", entity.getEntityType());
        broadcast.put("entity_id", entity.getEntityId());
        broadcast.put("sender_id", senderId);
        broadcast.put("content", entity.getContent());
        broadcast.put("created_at", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : "");

        // WebSocket 推送
        pushToRecipients(entity.getEntityType(), entity.getEntityId(), senderId, broadcast);

        return entity;
    }

    /**
     * 将新消息推送给对应会话的所有接收方
     */
    private void pushToRecipients(String entityType, String entityId, String senderId,
                                  Map<String, Object> broadcast) {
        try {
            String broadcastJson = objectMapper.writeValueAsString(broadcast);
            Set<String> targetUserIds = resolveTargetUserIds(entityType, entityId, senderId);
            for (String userId : targetUserIds) {
                sendToUser(userId, broadcastJson);
            }
        } catch (Exception e) {
            log.error("推送消息失败: {}", e.getMessage());
        }
    }

    /**
     * 解析消息的目标用户ID列表
     */
    private Set<String> resolveTargetUserIds(String entityType, String entityId, String senderId) {
        Set<String> userIds = new LinkedHashSet<>();
        userIds.add(senderId); // 始终包含发送方（多设备同步）

        if ("private".equals(entityType)) {
            String[] parts = entityId.split(":");
            if (parts.length == 2) {
                String otherUserId = parts[0].equals(senderId) ? parts[1] : parts[0];
                userIds.add(otherUserId);
            }
        } else if ("group".equals(entityType)) {
            String groupId = entityId.startsWith("team:") ? entityId.substring(5) : entityId;
            List<TeamMemberEntity> members = teamMemberMapper.selectList(
                    Wrappers.<TeamMemberEntity>lambdaQuery()
                            .eq(TeamMemberEntity::getTeamId, groupId));
            for (TeamMemberEntity m : members) {
                userIds.add(m.getUserId());
            }
        }
        return userIds;
    }

    private void sendToUser(String userId, String message) {
        WebSocketSession session = sessionManager.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.warn("发送消息给用户 {} 失败: {}", userId, e.getMessage());
            }
        }
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

        // 推送撤回通知
        Map<String, Object> recallMsg = new LinkedHashMap<>();
        recallMsg.put("type", "message_recalled");
        recallMsg.put("message_id", messageId);
        recallMsg.put("entity_type", entity.getEntityType());
        recallMsg.put("entity_id", entity.getEntityId());
        recallMsg.put("sender_id", userId);
        pushToRecipients(entity.getEntityType(), entity.getEntityId(), userId, recallMsg);
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
        List<Map<String, Object>> result = new ArrayList<>();

        // ======== 1. 私聊会话 ========
        List<ImMessageEntity> privateMessages = imMessageMapper.selectList(Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, "private")
                .and(w -> w.like(ImMessageEntity::getEntityId, "%" + userId + "%"))
                .orderByDesc(ImMessageEntity::getCreatedAt));

        Map<String, ImMessageEntity> latestPrivate = new LinkedHashMap<>();
        Set<String> seenPrivate = new HashSet<>();
        for (ImMessageEntity msg : privateMessages) {
            String eid = msg.getEntityId();
            if (eid.contains(userId) && !seenPrivate.contains(eid)) {
                seenPrivate.add(eid);
                latestPrivate.put(eid, msg);
            }
        }

        for (Map.Entry<String, ImMessageEntity> entry : latestPrivate.entrySet()) {
            String entityId = entry.getKey();
            ImMessageEntity lastMsg = entry.getValue();

            String[] parts = entityId.split(":");
            String otherUserId = parts[0].equals(userId) ? parts[1] : parts[0];

            UserEntity otherUser = userMapper.selectById(otherUserId);
            String otherNickname = otherUser != null ? otherUser.getNickname() : "未知用户";
            String otherAvatar = otherUser != null ? otherUser.getAvatarUrl() : "";

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

        // ======== 2. 群聊会话 ========
        List<TeamMemberEntity> myTeams = teamMemberMapper.selectList(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getUserId, userId));

        for (TeamMemberEntity myTeam : myTeams) {
            String groupEntityId = "team:" + myTeam.getTeamId();
            TeamEntity team = teamMapper.selectById(myTeam.getTeamId());
            if (team == null || !"active".equals(team.getStatus())) continue;

            List<ImMessageEntity> groupMsgs = imMessageMapper.selectList(Wrappers.<ImMessageEntity>lambdaQuery()
                    .eq(ImMessageEntity::getEntityType, "group")
                    .eq(ImMessageEntity::getEntityId, groupEntityId)
                    .orderByDesc(ImMessageEntity::getCreatedAt)
                    .last("LIMIT 1"));

            ImMessageEntity lastMsg = groupMsgs.isEmpty() ? null : groupMsgs.get(0);

            long unread = getUnreadCount("group", groupEntityId, userId);

            Map<String, Object> conv = new HashMap<>();
            conv.put("entity_type", "group");
            conv.put("entity_id", groupEntityId);
            conv.put("group_id", myTeam.getTeamId());
            conv.put("group_name", team.getName());
            conv.put("group_avatar_url", team.getAvatarUrl());
            conv.put("last_message", lastMsg != null ? lastMsg.getContent() : null);
            conv.put("last_message_type", lastMsg != null ? lastMsg.getType() : null);
            conv.put("last_message_time", lastMsg != null && lastMsg.getCreatedAt() != null
                    ? lastMsg.getCreatedAt().toString() : team.getCreatedAt().toString());
            conv.put("last_sender_id", lastMsg != null ? lastMsg.getSenderId() : null);
            conv.put("last_message_recalled", lastMsg != null && lastMsg.getRecalled());
            conv.put("unread_count", unread);
            result.add(conv);
        }

        result.sort((a, b) -> ((String) b.get("last_message_time")).compareTo((String) a.get("last_message_time")));
        return result;
    }

    @Override
    public void markRead(String entityType, String entityId, String userId) {
        if ("group".equals(entityType)) {
            String groupId = entityId.startsWith("team:") ? entityId.substring(5) : entityId;
            GroupChatReadMarkerEntity marker = groupChatReadMarkerMapper.selectOne(
                    Wrappers.<GroupChatReadMarkerEntity>lambdaQuery()
                            .eq(GroupChatReadMarkerEntity::getGroupId, groupId)
                            .eq(GroupChatReadMarkerEntity::getUserId, userId));
            LocalDateTime now = LocalDateTime.now();
            if (marker == null) {
                marker = new GroupChatReadMarkerEntity();
                marker.setGroupId(groupId);
                marker.setUserId(userId);
                marker.setLastReadAt(now);
                groupChatReadMarkerMapper.insert(marker);
            } else {
                marker.setLastReadAt(now);
                groupChatReadMarkerMapper.updateById(marker);
            }
        } else {
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
    }

    @Override
    public long getUnreadCount(String entityType, String entityId, String userId) {
        if ("group".equals(entityType)) {
            String groupId = entityId.startsWith("team:") ? entityId.substring(5) : entityId;
            GroupChatReadMarkerEntity marker = groupChatReadMarkerMapper.selectOne(
                    Wrappers.<GroupChatReadMarkerEntity>lambdaQuery()
                            .eq(GroupChatReadMarkerEntity::getGroupId, groupId)
                            .eq(GroupChatReadMarkerEntity::getUserId, userId));
            LocalDateTime lastReadAt = marker != null ? marker.getLastReadAt() : LocalDateTime.of(1970, 1, 1, 0, 0);
            return imMessageMapper.selectCount(Wrappers.<ImMessageEntity>lambdaQuery()
                    .eq(ImMessageEntity::getEntityType, entityType)
                    .eq(ImMessageEntity::getEntityId, entityId)
                    .ne(ImMessageEntity::getSenderId, userId)
                    .gt(ImMessageEntity::getCreatedAt, lastReadAt));
        } else {
            return imMessageMapper.selectCount(Wrappers.<ImMessageEntity>lambdaQuery()
                    .eq(ImMessageEntity::getEntityType, entityType)
                    .eq(ImMessageEntity::getEntityId, entityId)
                    .ne(ImMessageEntity::getSenderId, userId)
                    .isNull(ImMessageEntity::getReadAt));
        }
    }

    @Override
    public long getTotalUnreadCount(String userId) {
        long total = 0;

        total += imMessageMapper.selectCount(Wrappers.<ImMessageEntity>lambdaQuery()
                .eq(ImMessageEntity::getEntityType, "private")
                .like(ImMessageEntity::getEntityId, "%" + userId + "%")
                .ne(ImMessageEntity::getSenderId, userId)
                .isNull(ImMessageEntity::getReadAt));

        List<TeamMemberEntity> myTeams = teamMemberMapper.selectList(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getUserId, userId));
        for (TeamMemberEntity teamMember : myTeams) {
            String groupEntityId = "team:" + teamMember.getTeamId();
            total += getUnreadCount("group", groupEntityId, userId);
        }

        return total;
    }

    private String getOtherUserId(String entityId, String myUserId) {
        String[] parts = entityId.split(":");
        if (parts.length == 2) {
            return parts[0].equals(myUserId) ? parts[1] : parts[0];
        }
        return "";
    }
}
