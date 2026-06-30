package com.quju.platform.component.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.platform.entity.ImMessageEntity;
import com.quju.platform.mapper.ImMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ImWebSocketServer extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final ImMessageMapper imMessageMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null && query.startsWith("userId=")) {
                String userId = query.substring(7);
                sessionManager.put(userId, session);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
        // Expected: { entity_type, entity_id, sender_id, type, content, target_user_ids (optional) }
        String entityType = (String) payload.get("entity_type");
        String entityId = (String) payload.get("entity_id");
        String senderId = (String) payload.get("sender_id");
        String msgType = (String) payload.getOrDefault("type", "text");
        String content = (String) payload.get("content");

        // Save message
        ImMessageEntity msg = new ImMessageEntity();
        msg.setEntityType(entityType);
        msg.setEntityId(entityId);
        msg.setSenderId(senderId);
        msg.setType(msgType);
        msg.setContent(content);
        msg.setRecalled(false);
        msg.setCreatedAt(LocalDateTime.now());
        imMessageMapper.insert(msg);

        // Broadcast to all members of the entity (simplified: forward to specific users if provided)
        @SuppressWarnings("unchecked")
        java.util.List<String> targetUsers = (java.util.List<String>) payload.get("target_user_ids");
        if (targetUsers != null) {
            for (String uid : targetUsers) {
                WebSocketSession targetSession = sessionManager.get(uid);
                if (targetSession != null && targetSession.isOpen()) {
                    targetSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                            "type", "new_message",
                            "message_id", msg.getId(),
                            "entity_type", entityType,
                            "entity_id", entityId,
                            "sender_id", senderId,
                            "content", content,
                            "created_at", msg.getCreatedAt().toString()
                    ))));
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null && query.startsWith("userId=")) {
                String userId = query.substring(7);
                sessionManager.remove(userId);
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        URI uri = session.getUri();
        if (uri != null) {
            String query = uri.getQuery();
            if (query != null && query.startsWith("userId=")) {
                String userId = query.substring(7);
                sessionManager.remove(userId);
            }
        }
    }
}
