package com.quju.platform.component.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.TeamMemberEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.service.ImService;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ImWebSocketServer extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketServer.class);

    private final SessionManager sessionManager;
    private final ImService imService;
    private final TeamMemberMapper teamMemberMapper;
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

        Map<String, String> params = parseQueryParams(query);
        String token = params.get("token");
        if (token == null || token.isBlank()) {
            closeSilently(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

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

        // 复用 Service 层的校验 + 存储 + WebSocket 推送
        try {
            ImMessageDto dto = new ImMessageDto();
            dto.setEntityType(entityType);
            dto.setEntityId(entityId);
            dto.setType(msgType);
            dto.setContent(content);
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
            dto.setMetadata(metadata);

            imService.send(dto, senderId);
        } catch (BusinessException e) {
            session.sendMessage(new TextMessage(
                    "{\"type\":\"error\",\"message\":\"" + e.getMessage() + "\"}"));
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

        if ("private".equals(entityType)) {
            String[] parts = entityId.split(":");
            String otherUserId = parts[0].equals(userId) ? parts[1] : parts[0];
            sendToUser(otherUserId, json);
        } else if ("group".equals(entityType)) {
            broadcastToGroupExcludeSender(entityId, userId, json);
        }
    }

    private void handleMarkRead(WebSocketSession session, String userId,
                                Map<String, Object> payload) throws Exception {
        String entityType = (String) payload.get("entity_type");
        String entityId = (String) payload.get("entity_id");

        if (entityType == null || entityId == null) return;

        // 复用 Service 层的已读标记逻辑
        try {
            imService.markRead(entityType, entityId, userId);
        } catch (Exception e) {
            log.warn("标记已读失败: {}", e.getMessage());
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

    /**
     * 广播消息给群聊中除发送方外的所有在线成员（用于 typing 等瞬时状态）
     */
    private void broadcastToGroupExcludeSender(String entityId, String senderId, String message) {
        String groupId = entityId.startsWith("team:") ? entityId.substring(5) : entityId;
        List<TeamMemberEntity> members = teamMemberMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers
                        .<TeamMemberEntity>lambdaQuery()
                        .eq(TeamMemberEntity::getTeamId, groupId));
        for (TeamMemberEntity member : members) {
            if (!member.getUserId().equals(senderId)) {
                sendToUser(member.getUserId(), message);
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
