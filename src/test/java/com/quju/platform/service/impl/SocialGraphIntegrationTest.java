package com.quju.platform.service.impl;

import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.FollowMapper;
import com.quju.platform.mapper.FriendshipMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.SocialGraphService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("社交关系服务集成测试 (US26-28)")
class SocialGraphIntegrationTest {

    @Autowired private SocialGraphService socialGraphService;
    @Autowired private FriendshipMapper friendshipMapper;
    @Autowired private FollowMapper followMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private String userAId;
    private String userBId;

    @BeforeEach
    void setUp() {
        friendshipMapper.delete(null);
        followMapper.delete(null);
        userMapper.delete(null);
        SecurityContextHolder.clearContext();

        // 创建用户A
        UserEntity userA = new UserEntity();
        userA.setEmail("userA@example.com");
        userA.setPasswordHash(passwordEncoder.encode("Pass1234"));
        userA.setNickname("UserA");
        userA.setRole("personal");
        userA.setStatus("active");
        userA.setCreditScore(100);
        userA.setInterestTags(List.of());
        userMapper.insert(userA);
        userAId = userA.getId();

        // 创建用户B
        UserEntity userB = new UserEntity();
        userB.setEmail("userB@example.com");
        userB.setPasswordHash(passwordEncoder.encode("Pass1234"));
        userB.setNickname("UserB");
        userB.setRole("personal");
        userB.setStatus("active");
        userB.setCreditScore(100);
        userB.setInterestTags(List.of());
        userMapper.insert(userB);
        userBId = userB.getId();
    }

    private void authenticateAs(String uid) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(uid, null,
                        List.of(new SimpleGrantedAuthority("ROLE_personal"))));
    }

    @Nested @DisplayName("好友申请")
    class FriendRequestTests {

        @Test @DisplayName("发送好友申请成功")
        void shouldSendFriendRequest() {
            authenticateAs(userAId);
            Map<String, Object> result = socialGraphService.requestFriend(userAId, userBId);
            assertNotNull(result.get("request_id"));
            assertEquals("pending", result.get("status"));
        }

        @Test @DisplayName("接受好友申请")
        void shouldAcceptFriendRequest() {
            authenticateAs(userAId);
            Map<String, Object> sent = socialGraphService.requestFriend(userAId, userBId);
            String requestId = (String) sent.get("request_id");

            authenticateAs(userBId);
            socialGraphService.accept(requestId, userBId);

            // 验证好友关系（双向）
            List<Map<String, Object>> friendsOfA = socialGraphService.friends(userAId, null);
            List<Map<String, Object>> friendsOfB = socialGraphService.friends(userBId, null);
            assertTrue(friendsOfA.stream().anyMatch(f -> f.get("friend_id").equals(userBId)));
            assertTrue(friendsOfB.stream().anyMatch(f -> f.get("friend_id").equals(userAId)));
        }

        @Test @DisplayName("拒绝好友申请")
        void shouldRejectFriendRequest() {
            authenticateAs(userAId);
            Map<String, Object> sent = socialGraphService.requestFriend(userAId, userBId);
            String requestId = (String) sent.get("request_id");

            authenticateAs(userBId);
            socialGraphService.reject(requestId, userBId);

            assertTrue(socialGraphService.friends(userAId, null).isEmpty());
        }

        @Test @DisplayName("查看收到的好友申请")
        void shouldGetReceivedRequests() {
            authenticateAs(userAId);
            socialGraphService.requestFriend(userAId, userBId);

            authenticateAs(userBId);
            List<Map<String, Object>> received = socialGraphService.receivedRequests(userBId);
            assertFalse(received.isEmpty());
        }

        @Test @DisplayName("查看已发送的好友申请")
        void shouldGetSentRequests() {
            authenticateAs(userAId);
            socialGraphService.requestFriend(userAId, userBId);

            List<Map<String, Object>> sent = socialGraphService.sentRequests(userAId);
            assertFalse(sent.isEmpty());
        }
    }

    @Nested @DisplayName("关注")
    class FollowTests {

        @Test @DisplayName("关注用户成功")
        void shouldFollowUser() {
            authenticateAs(userAId);
            socialGraphService.follow(userAId, userBId);

            List<Map<String, Object>> following = socialGraphService.following(userAId);
            assertEquals(1, following.size());
            assertEquals(userBId, following.get(0).get("user_id"));
        }

        @Test @DisplayName("取消关注")
        void shouldUnfollowUser() {
            authenticateAs(userAId);
            socialGraphService.follow(userAId, userBId);
            socialGraphService.unfollow(userAId, userBId);

            List<Map<String, Object>> following = socialGraphService.following(userAId);
            assertTrue(following.isEmpty());
        }

        @Test @DisplayName("互相关注自动成为好友")
        void shouldAutoFriendOnMutualFollow() {
            authenticateAs(userAId);
            socialGraphService.follow(userAId, userBId);

            authenticateAs(userBId);
            socialGraphService.follow(userBId, userAId);

            List<Map<String, Object>> friendsOfA = socialGraphService.friends(userAId, null);
            assertTrue(friendsOfA.stream().anyMatch(f -> f.get("friend_id").equals(userBId)));
        }

        @Test @DisplayName("查看粉丝列表")
        void shouldGetFollowers() {
            authenticateAs(userAId);
            socialGraphService.follow(userAId, userBId);

            List<Map<String, Object>> followers = socialGraphService.followers(userBId);
            assertEquals(1, followers.size());
            assertEquals(userAId, followers.get(0).get("user_id"));
        }
    }

    @Nested @DisplayName("好友管理")
    class FriendManagementTests {

        @Test @DisplayName("好友列表支持分组标签筛选")
        void shouldFilterFriendsByGroupTag() {
            authenticateAs(userAId);
            Map<String, Object> sent = socialGraphService.requestFriend(userAId, userBId);

            authenticateAs(userBId);
            socialGraphService.accept((String) sent.get("request_id"), userBId);

            authenticateAs(userAId);
            socialGraphService.updateFriend(userAId, userBId, "好友B", List.of("徒步搭子"));

            List<Map<String, Object>> filtered = socialGraphService.friends(userAId, "徒步搭子");
            assertFalse(filtered.isEmpty());
        }

        @Test @DisplayName("修改好友备注名和分组")
        void shouldUpdateFriendRemark() {
            authenticateAs(userAId);
            Map<String, Object> sent = socialGraphService.requestFriend(userAId, userBId);

            authenticateAs(userBId);
            socialGraphService.accept((String) sent.get("request_id"), userBId);

            authenticateAs(userAId);
            socialGraphService.updateFriend(userAId, userBId, "新备注", List.of("好友"));

            List<Map<String, Object>> friends = socialGraphService.friends(userAId, null);
            Map<String, Object> friendInfo = friends.stream()
                    .filter(f -> f.get("friend_id").equals(userBId))
                    .findFirst().orElseThrow();
            assertEquals("新备注", friendInfo.get("remark_name"));
        }

        @Test @DisplayName("删除好友双向解除")
        void shouldDeleteFriendBidirectionally() {
            authenticateAs(userAId);
            Map<String, Object> sent = socialGraphService.requestFriend(userAId, userBId);

            authenticateAs(userBId);
            socialGraphService.accept((String) sent.get("request_id"), userBId);

            authenticateAs(userAId);
            socialGraphService.deleteFriend(userAId, userBId);

            assertTrue(socialGraphService.friends(userAId, null).isEmpty());
            assertTrue(socialGraphService.friends(userBId, null).isEmpty());
        }
    }
}
