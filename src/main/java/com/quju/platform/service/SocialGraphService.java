package com.quju.platform.service;

import java.util.List;
import java.util.Map;

public interface SocialGraphService {
    Map<String, Object> requestFriend(String userId, String targetUserId);
    void accept(String requestId, String userId);
    void reject(String requestId, String userId);
    List<Map<String, Object>> friends(String userId, String groupTag);
    void deleteFriend(String userId, String friendId);
    void follow(String userId, String targetUserId);
    void unfollow(String userId, String targetUserId);

    /** 查询收到的好友申请列表 */
    List<Map<String, Object>> receivedRequests(String userId);
    /** 查询已发送的好友申请列表 */
    List<Map<String, Object>> sentRequests(String userId);
    /** 更新好友备注名和分组标签 */
    void updateFriend(String userId, String friendId, String remarkName, List<String> groupTags);
    /** 查询粉丝列表 */
    List<Map<String, Object>> followers(String userId);
    /** 查询关注列表 */
    List<Map<String, Object>> following(String userId);
    /** 查询指定用户的粉丝列表 */
    List<Map<String, Object>> followersOfUser(String targetUserId);
    /** 查询指定用户的关注列表 */
    List<Map<String, Object>> followingOfUser(String targetUserId);

    /** 通过二维码添加好友 — 直接建立双向好友关系 */
    Map<String, Object> addByQrcode(String userId, String ownerUserId);
}
