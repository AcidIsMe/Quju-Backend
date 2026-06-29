package com.quju.platform.service;

import java.util.List;
import java.util.Map;

public interface SocialGraphService {
    Map<String, Object> requestFriend(String userId, String targetUserId);
    void accept(String requestId, String userId);
    void reject(String requestId, String userId);
    List<Map<String, Object>> friends(String userId);
    void deleteFriend(String userId, String friendId);
    void follow(String userId, String targetUserId);
    void unfollow(String userId, String targetUserId);
}
