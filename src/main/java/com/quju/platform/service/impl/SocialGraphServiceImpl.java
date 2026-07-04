package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.FollowEntity;
import com.quju.platform.entity.FriendshipEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.FollowMapper;
import com.quju.platform.mapper.FriendshipMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.ImService;
import com.quju.platform.service.NotificationService;
import com.quju.platform.service.SocialGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SocialGraphServiceImpl implements SocialGraphService {

    private final FriendshipMapper friendshipMapper;
    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final ImService imService;

    @Override
    public Map<String, Object> requestFriend(String userId, String targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(40000, "不能添加自己为好友");
        }

        // 检查目标用户是否存在
        UserEntity targetUser = userMapper.selectById(targetUserId);
        if (targetUser == null) {
            throw new BusinessException(40400, "目标用户不存在");
        }

        // 检查是否已经是好友（任一方向 accepted 即算是好友）
        long acceptedCount = friendshipMapper.selectCount(Wrappers.<FriendshipEntity>lambdaQuery()
                .and(w -> w.eq(FriendshipEntity::getUserId, userId)
                        .eq(FriendshipEntity::getFriendId, targetUserId)
                        .or()
                        .eq(FriendshipEntity::getUserId, targetUserId)
                        .eq(FriendshipEntity::getFriendId, userId))
                .eq(FriendshipEntity::getStatus, "accepted"));
        if (acceptedCount > 0) {
            throw new BusinessException(40001, "已经是好友关系");
        }

        // 检查是否被对方拉黑
        long blockedCount = friendshipMapper.selectCount(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, targetUserId)
                .eq(FriendshipEntity::getFriendId, userId)
                .eq(FriendshipEntity::getStatus, "blocked"));
        if (blockedCount > 0) {
            throw new BusinessException(40002, "无法发送好友请求，你已被对方拉黑");
        }

        // 检查是否已有待处理的请求
        long pendingCount = friendshipMapper.selectCount(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, userId)
                .eq(FriendshipEntity::getFriendId, targetUserId)
                .eq(FriendshipEntity::getStatus, "pending"));
        if (pendingCount > 0) {
            throw new BusinessException(40003, "已发送过好友请求，请等待对方处理");
        }

        // 检查对方是否已向我发送 pending 请求 → 自动接受并建立好友关系
        FriendshipEntity reversePending = friendshipMapper.selectOne(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, targetUserId)
                .eq(FriendshipEntity::getFriendId, userId)
                .eq(FriendshipEntity::getStatus, "pending"));
        if (reversePending != null) {
            accept(reversePending.getId(), userId);
            return Map.of("request_id", reversePending.getId(), "status", "accepted", "auto_upgraded", true);
        }

        FriendshipEntity friendship = new FriendshipEntity();
        friendship.setUserId(userId);
        friendship.setFriendId(targetUserId);
        friendship.setStatus("pending");
        friendship.setActionUserId(userId);
        friendship.setGroupTags(List.of());
        friendshipMapper.insert(friendship);

        // 发送好友请求通知
        UserEntity requester = userMapper.selectById(userId);
        String nickname = requester != null ? requester.getNickname() : "未知用户";
        notificationService.notify(
                targetUserId,
                "friend_request",
                "好友请求",
                nickname + " 请求添加你为好友",
                Map.of("request_id", friendship.getId(), "from_user_id", userId, "from_nickname", nickname)
        );

        return Map.of("request_id", friendship.getId(), "status", friendship.getStatus());
    }

    @Override
    public void accept(String requestId, String userId) {
        FriendshipEntity friendship = friendshipMapper.selectById(requestId);
        if (friendship == null) {
            throw new BusinessException(40400, "好友请求不存在");
        }
        if (!"pending".equals(friendship.getStatus())) {
            throw new BusinessException(40001, "好友请求状态异常");
        }
        if (!friendship.getFriendId().equals(userId)) {
            throw new BusinessException(40300, "无权操作该好友请求");
        }

        // 更新原请求为已接受
        friendship.setStatus("accepted");
        friendshipMapper.updateById(friendship);

        // 创建逆向好友关系记录（双向好友）
        FriendshipEntity reverse = new FriendshipEntity();
        reverse.setUserId(friendship.getFriendId());
        reverse.setFriendId(friendship.getUserId());
        reverse.setStatus("accepted");
        reverse.setActionUserId(userId);
        reverse.setGroupTags(List.of());
        friendshipMapper.insert(reverse);

        // 通知请求方
        UserEntity accepter = userMapper.selectById(userId);
        String nickname = accepter != null ? accepter.getNickname() : "未知用户";
        notificationService.notify(
                friendship.getUserId(),
                "friend_accepted",
                "好友请求已通过",
                nickname + " 已接受你的好友请求",
                Map.of("friend_id", userId, "friend_nickname", nickname)
        );

        // 自动发送好友问候消息
        sendFriendGreeting(friendship.getUserId(), userId);
    }

    /**
     * 好友关系建立后，自动发送一条"我们已经是好友啦！"消息
     */
    private void sendFriendGreeting(String userA, String userB) {
        try {
            // 私聊 entity_id 按字母序拼接
            String entityId;
            if (userA.compareTo(userB) < 0) {
                entityId = userA + ":" + userB;
            } else {
                entityId = userB + ":" + userA;
            }

            ImMessageDto dto = new ImMessageDto();
            dto.setEntityType("private");
            dto.setEntityId(entityId);
            dto.setType("text");
            dto.setContent("我们已经是好友啦！");
            dto.setMentionAll(false);
            dto.setMentionUserIds(List.of());
            dto.setMetadata(Map.of());

            imService.send(dto, userB);
        } catch (Exception e) {
            // 消息发送失败不影响好友关系建立
        }
    }

    @Override
    public void reject(String requestId, String userId) {
        FriendshipEntity friendship = friendshipMapper.selectById(requestId);
        if (friendship == null) {
            throw new BusinessException(40400, "好友请求不存在");
        }
        if (!friendship.getFriendId().equals(userId)) {
            throw new BusinessException(40300, "无权操作该好友请求");
        }
        if (!"pending".equals(friendship.getStatus())) {
            throw new BusinessException(40001, "好友请求状态异常");
        }
        friendship.setStatus("blocked");
        friendshipMapper.updateById(friendship);
    }

    @Override
    public List<Map<String, Object>> friends(String userId, String groupTag) {
        var records = friendshipMapper.selectList(Wrappers.<FriendshipEntity>lambdaQuery()
                        .eq(FriendshipEntity::getUserId, userId)
                        .eq(FriendshipEntity::getStatus, "accepted"))
                .stream()
                .toList();
        // Filter by group_tag if provided
        if (groupTag != null && !groupTag.isBlank()) {
            records = records.stream()
                    .filter(f -> f.getGroupTags() != null && f.getGroupTags().contains(groupTag))
                    .toList();
        }
        return records.stream()
                .map(item -> {
                    UserEntity friend = userMapper.selectById(item.getFriendId());
                    Map<String, Object> friendMap = new HashMap<>();
                    friendMap.put("friend_id", item.getFriendId());
                    friendMap.put("nickname", friend != null && friend.getNickname() != null ? friend.getNickname() : "");
                    friendMap.put("avatar_url", friend != null && friend.getAvatarUrl() != null ? friend.getAvatarUrl() : "");
                    friendMap.put("remark_name", item.getRemarkName() == null ? "" : item.getRemarkName());
                    friendMap.put("group_tags", item.getGroupTags() == null ? List.of() : item.getGroupTags());
                    friendMap.put("created_at", item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
                    return friendMap;
                })
                .toList();
    }

    @Override
    public void deleteFriend(String userId, String friendId) {
        // 双向删除好友关系（包含备注名和分组标签一并清除）
        friendshipMapper.delete(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, userId)
                .eq(FriendshipEntity::getFriendId, friendId));
        friendshipMapper.delete(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, friendId)
                .eq(FriendshipEntity::getFriendId, userId));
    }

    @Override
    public void follow(String userId, String targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(40000, "不能关注自己");
        }

        // 检查是否已关注
        long existingCount = followMapper.selectCount(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFollowerId, userId)
                .eq(FollowEntity::getFollowedId, targetUserId));
        if (existingCount > 0) {
            throw new BusinessException(40001, "已关注该用户");
        }

        // 创建关注记录
        FollowEntity follow = new FollowEntity();
        follow.setFollowerId(userId);
        follow.setFollowedId(targetUserId);
        followMapper.insert(follow);

        // 检查对方是否已关注我（互关 → 自动成为好友）
        long mutualCount = followMapper.selectCount(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFollowerId, targetUserId)
                .eq(FollowEntity::getFollowedId, userId));
        if (mutualCount > 0) {
            // 创建双向好友关系
            FriendshipEntity f1 = new FriendshipEntity();
            f1.setUserId(userId);
            f1.setFriendId(targetUserId);
            f1.setStatus("accepted");
            f1.setActionUserId(userId);
            f1.setGroupTags(List.of());
            friendshipMapper.insert(f1);

            FriendshipEntity f2 = new FriendshipEntity();
            f2.setUserId(targetUserId);
            f2.setFriendId(userId);
            f2.setStatus("accepted");
            f2.setActionUserId(targetUserId);
            f2.setGroupTags(List.of());
            friendshipMapper.insert(f2);

            // 通知双方已成为好友
            UserEntity targetUser = userMapper.selectById(targetUserId);
            String targetNickname = targetUser != null ? targetUser.getNickname() : "未知用户";
            UserEntity currentUser = userMapper.selectById(userId);
            String currentNickname = currentUser != null ? currentUser.getNickname() : "未知用户";

            notificationService.notify(userId, "friend_added", "好友添加",
                    "你和 " + targetNickname + " 互相关注，已成为好友",
                    Map.of("friend_id", targetUserId, "friend_nickname", targetNickname));
            notificationService.notify(targetUserId, "friend_added", "好友添加",
                    "你和 " + currentNickname + " 互相关注，已成为好友",
                    Map.of("friend_id", userId, "friend_nickname", currentNickname));
        } else {
            // 仅通知对方有新的粉丝
            UserEntity currentUser = userMapper.selectById(userId);
            String currentNickname = currentUser != null ? currentUser.getNickname() : "未知用户";
            notificationService.notify(targetUserId, "new_follower", "新粉丝",
                    currentNickname + " 关注了你",
                    Map.of("follower_id", userId, "follower_nickname", currentNickname));
        }
    }

    @Override
    public void unfollow(String userId, String targetUserId) {
        // 删除关注记录
        followMapper.delete(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFollowerId, userId)
                .eq(FollowEntity::getFollowedId, targetUserId));

        // 如果存在好友关系，一并解除（取消关注即解除好友）
        friendshipMapper.delete(Wrappers.<FriendshipEntity>lambdaQuery()
                .and(w -> w.eq(FriendshipEntity::getUserId, userId)
                        .eq(FriendshipEntity::getFriendId, targetUserId)
                        .or()
                        .eq(FriendshipEntity::getUserId, targetUserId)
                        .eq(FriendshipEntity::getFriendId, userId))
                .eq(FriendshipEntity::getStatus, "accepted"));
    }

    @Override
    public List<Map<String, Object>> receivedRequests(String userId) {
        return friendshipMapper.selectList(Wrappers.<FriendshipEntity>lambdaQuery()
                        .eq(FriendshipEntity::getFriendId, userId)
                        .eq(FriendshipEntity::getStatus, "pending")
                        .ne(FriendshipEntity::getActionUserId, userId))
                .stream()
                .map(f -> {
                    UserEntity requester = userMapper.selectById(f.getUserId());
                    Map<String, Object> reqMap = new HashMap<>();
                    reqMap.put("request_id", f.getId());
                    reqMap.put("user_id", f.getUserId());
                    reqMap.put("nickname", requester != null && requester.getNickname() != null ? requester.getNickname() : "");
                    reqMap.put("avatar_url", requester != null && requester.getAvatarUrl() != null ? requester.getAvatarUrl() : "");
                    reqMap.put("created_at", f.getCreatedAt() != null ? f.getCreatedAt().toString() : "");
                    return reqMap;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> sentRequests(String userId) {
        return friendshipMapper.selectList(Wrappers.<FriendshipEntity>lambdaQuery()
                        .eq(FriendshipEntity::getUserId, userId)
                        .eq(FriendshipEntity::getStatus, "pending"))
                .stream()
                .map(f -> {
                    UserEntity targetUser = userMapper.selectById(f.getFriendId());
                    Map<String, Object> sentMap = new HashMap<>();
                    sentMap.put("request_id", f.getId());
                    sentMap.put("target_user_id", f.getFriendId());
                    sentMap.put("nickname", targetUser != null && targetUser.getNickname() != null ? targetUser.getNickname() : "");
                    sentMap.put("avatar_url", targetUser != null && targetUser.getAvatarUrl() != null ? targetUser.getAvatarUrl() : "");
                    sentMap.put("created_at", f.getCreatedAt() != null ? f.getCreatedAt().toString() : "");
                    return sentMap;
                })
                .toList();
    }

    @Override
    public void updateFriend(String userId, String friendId, String remarkName, List<String> groupTags) {
        FriendshipEntity friendship = friendshipMapper.selectOne(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, userId)
                .eq(FriendshipEntity::getFriendId, friendId)
                .eq(FriendshipEntity::getStatus, "accepted"));

        if (friendship == null) {
            throw new BusinessException(40400, "好友关系不存在");
        }

        if (remarkName != null) {
            if (remarkName.length() > 30) {
                throw new BusinessException(40000, "备注名不能超过30个字符");
            }
            friendship.setRemarkName(remarkName);
        }
        if (groupTags != null) {
            friendship.setGroupTags(groupTags);
        }
        friendshipMapper.updateById(friendship);
    }

    @Override
    public List<Map<String, Object>> followers(String userId) {
        return followMapper.selectList(Wrappers.<FollowEntity>lambdaQuery()
                        .eq(FollowEntity::getFollowedId, userId))
                .stream()
                .map(f -> {
                    UserEntity user = userMapper.selectById(f.getFollowerId());
                    Map<String, Object> map = new HashMap<>();
                    map.put("user_id", f.getFollowerId());
                    map.put("nickname", user != null && user.getNickname() != null ? user.getNickname() : "");
                    map.put("avatar_url", user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
                    return map;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> following(String userId) {
        return followMapper.selectList(Wrappers.<FollowEntity>lambdaQuery()
                        .eq(FollowEntity::getFollowerId, userId))
                .stream()
                .map(f -> {
                    UserEntity user = userMapper.selectById(f.getFollowedId());
                    Map<String, Object> map = new HashMap<>();
                    map.put("user_id", f.getFollowedId());
                    map.put("nickname", user != null && user.getNickname() != null ? user.getNickname() : "");
                    map.put("avatar_url", user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
                    return map;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> followersOfUser(String targetUserId) {
        return followMapper.selectList(Wrappers.<FollowEntity>lambdaQuery()
                        .eq(FollowEntity::getFollowedId, targetUserId))
                .stream()
                .map(f -> {
                    UserEntity user = userMapper.selectById(f.getFollowerId());
                    Map<String, Object> map = new HashMap<>();
                    map.put("user_id", f.getFollowerId());
                    map.put("nickname", user != null && user.getNickname() != null ? user.getNickname() : "");
                    map.put("avatar_url", user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
                    return map;
                })
                .toList();
    }

    @Override
    public List<Map<String, Object>> followingOfUser(String targetUserId) {
        return followMapper.selectList(Wrappers.<FollowEntity>lambdaQuery()
                        .eq(FollowEntity::getFollowerId, targetUserId))
                .stream()
                .map(f -> {
                    UserEntity user = userMapper.selectById(f.getFollowedId());
                    Map<String, Object> map = new HashMap<>();
                    map.put("user_id", f.getFollowedId());
                    map.put("nickname", user != null && user.getNickname() != null ? user.getNickname() : "");
                    map.put("avatar_url", user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
                    return map;
                }).toList();
    }

    @Override
    public Map<String, Object> addByQrcode(String userId, String ownerUserId) {
        if (userId.equals(ownerUserId)) {
            throw new BusinessException(40003, "不能添加自己为好友");
        }

        // 检查目标用户是否存在
        UserEntity targetUser = userMapper.selectById(ownerUserId);
        if (targetUser == null) {
            throw new BusinessException(40400, "用户不存在");
        }

        // 检查是否已经是好友
        long acceptedCount = friendshipMapper.selectCount(Wrappers.<FriendshipEntity>lambdaQuery()
                .and(w -> w.eq(FriendshipEntity::getUserId, userId)
                        .eq(FriendshipEntity::getFriendId, ownerUserId)
                        .or()
                        .eq(FriendshipEntity::getUserId, ownerUserId)
                        .eq(FriendshipEntity::getFriendId, userId))
                .eq(FriendshipEntity::getStatus, "accepted"));
        if (acceptedCount > 0) {
            return Map.of("already_friend", true);
        }

        // 检查是否被对方拉黑
        long blockedCount = friendshipMapper.selectCount(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, ownerUserId)
                .eq(FriendshipEntity::getFriendId, userId)
                .eq(FriendshipEntity::getStatus, "blocked"));
        if (blockedCount > 0) {
            throw new BusinessException(40002, "无法添加好友，你已被对方拉黑");
        }

        // 检查对方是否已向我发送 pending → 自动接受
        FriendshipEntity reversePending = friendshipMapper.selectOne(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, ownerUserId)
                .eq(FriendshipEntity::getFriendId, userId)
                .eq(FriendshipEntity::getStatus, "pending"));
        if (reversePending != null) {
            accept(reversePending.getId(), userId);
            return Map.of("already_friend", false, "auto_accepted", true);
        }

        // 检查我是否已向对方发送 pending → 直接升级为好友
        FriendshipEntity myPending = friendshipMapper.selectOne(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, userId)
                .eq(FriendshipEntity::getFriendId, ownerUserId)
                .eq(FriendshipEntity::getStatus, "pending"));
        if (myPending != null) {
            myPending.setStatus("accepted");
            friendshipMapper.updateById(myPending);
            // 创建逆向关系
            FriendshipEntity reverse = new FriendshipEntity();
            reverse.setUserId(ownerUserId);
            reverse.setFriendId(userId);
            reverse.setStatus("accepted");
            reverse.setActionUserId(userId);
            reverse.setGroupTags(List.of());
            friendshipMapper.insert(reverse);
        } else {
            // 直接建立双向好友关系
            FriendshipEntity f1 = new FriendshipEntity();
            f1.setUserId(userId);
            f1.setFriendId(ownerUserId);
            f1.setStatus("accepted");
            f1.setActionUserId(userId);
            f1.setGroupTags(List.of());
            friendshipMapper.insert(f1);

            FriendshipEntity f2 = new FriendshipEntity();
            f2.setUserId(ownerUserId);
            f2.setFriendId(userId);
            f2.setStatus("accepted");
            f2.setActionUserId(userId);
            f2.setGroupTags(List.of());
            friendshipMapper.insert(f2);
        }

        // 发送通知给双方
        UserEntity currentUser = userMapper.selectById(userId);
        String currentNickname = currentUser != null ? currentUser.getNickname() : "未知用户";
        String targetNickname = targetUser.getNickname() != null ? targetUser.getNickname() : "未知用户";

        notificationService.notify(userId, "friend_added", "好友添加",
                "你和 " + targetNickname + " 通过扫码成为好友",
                Map.of("friend_id", ownerUserId, "friend_nickname", targetNickname));
        notificationService.notify(ownerUserId, "friend_added", "好友添加",
                currentNickname + " 通过扫码添加你为好友",
                Map.of("friend_id", userId, "friend_nickname", currentNickname));

        return Map.of("already_friend", false);
    }
}
