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
    public ApiResponse<Map<String, Object>> request(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                                    @RequestBody Map<String, String> body) {
        return ApiResponse.ok(socialGraphService.requestFriend(SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId), body.get("target_user_id")));
    }

    @GetMapping("/friends")
    public ApiResponse<List<Map<String, Object>>> friends(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.ok(socialGraphService.friends(SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId)));
    }

    @PostMapping("/friends/requests/{id}/accept")
    public ApiResponse<Void> accept(@PathVariable String id, @RequestHeader(value = "X-User-Id", required = false) String userId) {
        socialGraphService.accept(id, SecurityUtil.currentUserIdOr(userId));
        return ApiResponse.ok();
    }

    @PostMapping("/friends/requests/{id}/reject")
    public ApiResponse<Void> reject(@PathVariable String id, @RequestHeader(value = "X-User-Id", required = false) String userId) {
        socialGraphService.reject(id, SecurityUtil.currentUserIdOr(userId));
        return ApiResponse.ok();
    }

    @DeleteMapping("/friends/{targetUserId}")
    public ApiResponse<Void> delete(@PathVariable String targetUserId, @RequestHeader(value = "X-User-Id", required = false) String userId) {
        socialGraphService.deleteFriend(SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId), targetUserId);
        return ApiResponse.ok();
    }

    @PostMapping("/follows/{targetUserId}")
    public ApiResponse<Void> follow(@PathVariable String targetUserId, @RequestHeader(value = "X-User-Id", required = false) String userId) {
        socialGraphService.follow(SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId), targetUserId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/follows/{targetUserId}")
    public ApiResponse<Void> unfollow(@PathVariable String targetUserId, @RequestHeader(value = "X-User-Id", required = false) String userId) {
        socialGraphService.unfollow(SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId), targetUserId);
        return ApiResponse.ok();
    }
}
