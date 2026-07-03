package com.quju.platform.controller.social;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.SocialGraphService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
public class FriendshipController {

    private final SocialGraphService socialGraphService;
    private final UserMapper userMapper;

    /** 二维码 token 存储：token → { userId, expiresAt }，后续迁移 Redis */
    private final ConcurrentHashMap<String, Map<String, Object>> qrcodeStore = new ConcurrentHashMap<>();

    @PostMapping("/friends/requests")
    public ApiResponse<Map<String, Object>> request(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(socialGraphService.requestFriend(SecurityUtil.requireCurrentUserId(), body.get("target_user_id")));
    }

    @GetMapping("/friends")
    public ApiResponse<List<Map<String, Object>>> friends(@RequestParam(required = false) String groupTag) {
        return ApiResponse.ok(socialGraphService.friends(SecurityUtil.requireCurrentUserId(), groupTag));
    }

    @PostMapping("/friends/requests/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable String id) {
        socialGraphService.accept(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/friends/requests/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable String id) {
        socialGraphService.reject(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/friends/{targetUserId}")
    public ApiResponse<Void> delete(@PathVariable String targetUserId) {
        socialGraphService.deleteFriend(SecurityUtil.requireCurrentUserId(), targetUserId);
        return ApiResponse.ok();
    }

    @PostMapping("/follows/{targetUserId}")
    public ApiResponse<Void> follow(@PathVariable String targetUserId) {
        socialGraphService.follow(SecurityUtil.requireCurrentUserId(), targetUserId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/follows/{targetUserId}")
    public ApiResponse<Void> unfollow(@PathVariable String targetUserId) {
        socialGraphService.unfollow(SecurityUtil.requireCurrentUserId(), targetUserId);
        return ApiResponse.ok();
    }

    // ======================== US26-28 新增接口 ========================

    /** 查询收到的好友申请 */
    @GetMapping("/friends/requests/received")
    public ApiResponse<List<Map<String, Object>>> receivedRequests() {
        return ApiResponse.ok(socialGraphService.receivedRequests(SecurityUtil.requireCurrentUserId()));
    }

    /** 查询已发送的好友申请 */
    @GetMapping("/friends/requests/sent")
    public ApiResponse<List<Map<String, Object>>> sentRequests() {
        return ApiResponse.ok(socialGraphService.sentRequests(SecurityUtil.requireCurrentUserId()));
    }

    /** 更新好友备注名和分组标签 */
    @PatchMapping("/friends/{friendId}")
    public ApiResponse<Void> updateFriend(@PathVariable String friendId, @RequestBody Map<String, Object> body) {
        String remarkName = (String) body.get("remark_name");
        @SuppressWarnings("unchecked")
        List<String> groupTags = (List<String>) body.get("group_tags");
        socialGraphService.updateFriend(SecurityUtil.requireCurrentUserId(), friendId, remarkName, groupTags);
        return ApiResponse.ok();
    }

    /** 查询指定用户的粉丝列表 */
    @GetMapping("/users/{userId}/followers")
    public ApiResponse<List<Map<String, Object>>> followers(@PathVariable String userId) {
        return ApiResponse.ok(socialGraphService.followersOfUser(userId));
    }

    /** 查询指定用户的关注列表 */
    @GetMapping("/users/{userId}/following")
    public ApiResponse<List<Map<String, Object>>> following(@PathVariable String userId) {
        return ApiResponse.ok(socialGraphService.followingOfUser(userId));
    }

    // ======================== 扫码添加好友 ========================

    /** 生成当前用户的加好友二维码 token（5分钟有效，一次性） */
    @GetMapping("/users/me/qrcode")
    public ApiResponse<Map<String, Object>> generateQrcode() {
        String userId = SecurityUtil.requireCurrentUserId();
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);

        qrcodeStore.put(token, Map.of("userId", userId, "expiresAt", expiresAt));

        UserEntity user = userMapper.selectById(userId);
        return ApiResponse.ok(Map.of(
                "token", token,
                "expires_at", expiresAt.toString(),
                "nickname", user != null ? user.getNickname() : "",
                "avatar_url", user != null && user.getAvatarUrl() != null ? user.getAvatarUrl() : ""
        ));
    }

    /** 通过扫描二维码添加好友 */
    @PostMapping("/friends/add-by-qrcode")
    public ApiResponse<Map<String, Object>> addByQrcode(@RequestBody Map<String, String> body) {
        String token = body.get("qrcode_token");
        if (token == null || token.isBlank()) {
            throw new com.quju.platform.exception.BusinessException(40000, "无效的二维码");
        }

        Map<String, Object> entry = qrcodeStore.get(token);
        if (entry == null) {
            throw new com.quju.platform.exception.BusinessException(40001, "二维码已失效或不存在");
        }

        LocalDateTime expiresAt = (LocalDateTime) entry.get("expiresAt");
        if (expiresAt.isBefore(LocalDateTime.now())) {
            qrcodeStore.remove(token);
            throw new com.quju.platform.exception.BusinessException(40002, "二维码已过期，请让对方重新生成");
        }

        String ownerUserId = (String) entry.get("userId");
        String currentUserId = SecurityUtil.requireCurrentUserId();

        if (currentUserId.equals(ownerUserId)) {
            throw new com.quju.platform.exception.BusinessException(40003, "不能添加自己为好友");
        }

        // 使 token 一次性失效
        qrcodeStore.remove(token);

        // 调用服务层处理好友添加逻辑
        Map<String, Object> result = socialGraphService.addByQrcode(currentUserId, ownerUserId);
        return ApiResponse.ok(result);
    }
}
