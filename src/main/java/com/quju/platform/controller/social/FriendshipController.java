package com.quju.platform.controller.social;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.service.SocialGraphService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class FriendshipController {

    private final SocialGraphService socialGraphService;

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
}
