package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.FollowEntity;
import com.quju.platform.entity.FriendshipEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.mapper.FollowMapper;
import com.quju.platform.mapper.FriendshipMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.NotificationService;
import com.quju.platform.service.SocialGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    @Override
    public Map<String, Object> requestFriend(String userId, String targetUserId) {
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
        if (friendship != null) {
            friendship.setStatus("accepted");
            friendshipMapper.updateById(friendship);
        }
    }

    @Override
    public void reject(String requestId, String userId) {
        FriendshipEntity friendship = friendshipMapper.selectById(requestId);
        if (friendship != null) {
            friendship.setStatus("blocked");
            friendshipMapper.updateById(friendship);
        }
    }

    @Override
    public List<Map<String, Object>> friends(String userId) {
        return friendshipMapper.selectList(Wrappers.<FriendshipEntity>lambdaQuery()
                        .eq(FriendshipEntity::getUserId, userId)
                        .eq(FriendshipEntity::getStatus, "accepted"))
                .stream()
                .map(item -> Map.<String, Object>of(
                        "friend_id", item.getFriendId(),
                        "remark_name", item.getRemarkName() == null ? "" : item.getRemarkName(),
                        "group_tags", item.getGroupTags() == null ? List.of() : item.getGroupTags()))
                .toList();
    }

    @Override
    public void deleteFriend(String userId, String friendId) {
        friendshipMapper.delete(Wrappers.<FriendshipEntity>lambdaQuery()
                .eq(FriendshipEntity::getUserId, userId)
                .eq(FriendshipEntity::getFriendId, friendId));
    }

    @Override
    public void follow(String userId, String targetUserId) {
        FollowEntity follow = new FollowEntity();
        follow.setFollowerId(userId);
        follow.setFollowedId(targetUserId);
        followMapper.insert(follow);
    }

    @Override
    public void unfollow(String userId, String targetUserId) {
        followMapper.delete(Wrappers.<FollowEntity>lambdaQuery()
                .eq(FollowEntity::getFollowerId, userId)
                .eq(FollowEntity::getFollowedId, targetUserId));
    }
}
