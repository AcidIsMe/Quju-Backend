package com.quju.platform.component.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void put(String userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public WebSocketSession get(String userId) {
        return sessions.get(userId);
    }

    public void remove(String userId) {
        sessions.remove(userId);
    }
}
