package com.quju.platform.component.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.platform.entity.ImMessageEntity;
import com.quju.platform.mapper.ImMessageMapper;
import com.quju.platform.mapper.FriendshipMapper;
import com.quju.platform.util.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ImWebSocketServer extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketServer.class);

    private final SessionManager sessionManager;
    private final ImMessageMapper imMessageMapper;
    private final FriendshipMapper friendshipMapper;
    private final ObjectMapper objectMapper;
    private final JwtTokenUtil jwtTokenUtil;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            closeSilently(session, CloseStatus.BAD_DATA);
            return;
        }

        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            closeSilently(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Parse query parameters
        Map<String, String> params = parseQueryParams(query);
        String token = params.get("token");
        if (token == null || token.isBlank()) {
            closeSilently(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Validate JWT token
        String userId;
        try {
            Claims claims = jwtTokenUtil.parse(token);
            userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                closeSilently(session, CloseStatus.POLICY_VIOLATION);
                return;
            }
        } catch (Exception e) {
            log.warn("WebSocket authentication failed: {}", e.getMessage());
            closeSilently(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Store session with userId for multiple device support
        sessionManager.put(userId, session);
        log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = findUserIdBySession(session);
        if (userId == null) {
            session.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"未认证\"}"));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
        String msgType = (String) payload.getOrDefault("type", "");

        switch (msgType) {
            case "send_message" -> handleSendMessage(session, userId, payload);
            case "typing" -> handleTyping(session, userId, payload);
            case "mark_read" -> handleMarkRead(session, userId, payload);
            case "ping" -> handlePing(session);
            default -> session.sendMessage(new TextMessage(
                    "{\"type\":\"error\",\"message\":\"未知消息类型:" + msgType + "\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = findUserIdBySession(session);
        if (userId != null) {
            sessionManager.remove(userId);
            log.info("WebSocket disconnected: userId={}, sessionId={}", userId, session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = findUserIdBySession(session);
        if (userId != null) {
            sessionManager.remove(userId);
            log.warn("WebSocket transport error: userId={}, sessionId={}", userId, session.getId());
        }
    }

    // ======================== 消息处理 ========================

    private void handleSendMessage(WebSocketSession session, String senderId,
                                   Map<String, Object> payload) throws Exception {
        String entityType = (String) payload.get("entity_type");
        String entityId = (String) payload.get("entity_id");
        String msgType = (String) payload.getOrDefault("type", "text");
        String content = (String) payload.get("content");

        if (entityType == null || entityId == null || content == null || content.isBlank()) {
            session.sendMessage(new TextMessage(
                    "{\"type\":\"error\",\"message\":\"entity_type、entity_id、content 不能为空\"}"));
            return;
        }

        // 私聊模式校验好友关系
        if ("private".equals(entityType)) {
            String[] parts = entityId.split(":");
            if (parts.length != 2) {
                session.sendMessage(new TextMessage(
                        "{\"type\":\"error\",\"message\":\"私聊entity_id格式错误\"}"));
                return;
            }
            String otherUserId = parts[0].equals(senderId) ? parts[1] : parts[0];

            // 校验是否好友
            long friendCount = friendshipMapper.selectCount(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers
                            .<com.quju.platform.entity.FriendshipEntity>lambdaQuery()
                            .and(w -> w
                                    .eq(com.quju.platform.entity.FriendshipEntity::getUserId, senderId)
                                    .eq(com.quju.platform.entity.FriendshipEntity::getFriendId, otherUserId)
                                    .or()
                                    .eq(com.quju.platform.entity.FriendshipEntity::getUserId, otherUserId)
                                    .eq(com.quju.platform.entity.FriendshipEntity::getFriendId, senderId))
                            .eq(com.quju.platform.entity.FriendshipEntity::getStatus, "accepted"));
            if (friendCount == 0) {
                session.sendMessage(new TextMessage(
                        "{\"type\":\"error\",\"message\":\"不是好友关系，无法发送消息\"}"));
                return;
            }
        }

        // 保存消息
        ImMessageEntity msg = new ImMessageEntity();
        msg.setEntityType(entityType);
        msg.setEntityId(entityId);
        msg.setSenderId(senderId);
        msg.setType(msgType);
        msg.setContent(content);
        msg.setRecalled(false);
        msg.setCreatedAt(LocalDateTime.now());
        imMessageMapper.insert(msg);

        // 构建广播消息
        Map<String, Object> broadcast = new LinkedHashMap<>();
        broadcast.put("type", "new_message");
        broadcast.put("message_id", msg.getId());
        broadcast.put("entity_type", entityType);
        broadcast.put("entity_id", entityId);
        broadcast.put("sender_id", senderId);
        broadcast.put("content", content);
        broadcast.put("created_at", msg.getCreatedAt().toString());
        String broadcastJson = objectMapper.writeValueAsString(broadcast);

        // 私聊：发送给发送方和接收方
        if ("private".equals(entityType)) {
            String[] parts = entityId.split(":");
            String otherUserId = parts[0].equals(senderId) ? parts[1] : parts[0];
            sendToUser(senderId, broadcastJson);
            sendToUser(otherUserId, broadcastJson);
        } else {
            // 群聊等其他模式：仅发送给发送方确认
            sendToUser(senderId, broadcastJson);
        }
    }

    private void handleTyping(WebSocketSession session, String userId,
                              Map<String, Object> payload) throws Exception {
        String entityType = (String) payload.get("entity_type");
        String entityId = (String) payload.get("entity_id");

        if (entityType == null || entityId == null) return;

        Map<String, Object> typingMsg = new LinkedHashMap<>();
        typingMsg.put("type", "typing");
        typingMsg.put("entity_type", entityType);
        typingMsg.put("entity_id", entityId);
        typingMsg.put("user_id", userId);
        String json = objectMapper.writeValueAsString(typingMsg);

        // 私聊：转发给对方
        if ("private".equals(entityType)) {
            String[] parts = entityId.split(":");
            String otherUserId = parts[0].equals(userId) ? parts[1] : parts[0];
            sendToUser(otherUserId, json);
        }
    }

    private void handleMarkRead(WebSocketSession session, String userId,
                                Map<String, Object> payload) throws Exception {
        String entityType = (String) payload.get("entity_type");
        String entityId = (String) payload.get("entity_id");

        if (entityType == null || entityId == null) return;

        // 标记对方发送的消息为已读
        String otherUserId = getOtherUserId(entityId, userId);
        if (otherUserId != null) {
            List<ImMessageEntity> unread = imMessageMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers
                            .<ImMessageEntity>lambdaQuery()
                            .eq(ImMessageEntity::getEntityType, entityType)
                            .eq(ImMessageEntity::getEntityId, entityId)
                            .eq(ImMessageEntity::getSenderId, otherUserId)
                            .isNull(ImMessageEntity::getReadAt));
            for (ImMessageEntity m : unread) {
                m.setReadAt(LocalDateTime.now());
                imMessageMapper.updateById(m);
            }
        }

        // 回复已读确认
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "read_ack");
        ack.put("entity_type", entityType);
        ack.put("entity_id", entityId);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
    }

    private void handlePing(WebSocketSession session) throws Exception {
        session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
    }

    // ======================== 工具方法 ========================

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

    private void closeSilently(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ignored) {
        }
    }

    private String findUserIdBySession(WebSocketSession session) {
        for (Map.Entry<String, WebSocketSession> entry : sessionManager.getAll().entrySet()) {
            if (entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getOtherUserId(String entityId, String myUserId) {
        String[] parts = entityId.split(":");
        if (parts.length == 2) {
            return parts[0].equals(myUserId) ? parts[1] : parts[0];
        }
        return null;
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isBlank()) return params;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2) {
                params.put(pair[0], pair[1]);
            }
        }
        return params;
    }
}
