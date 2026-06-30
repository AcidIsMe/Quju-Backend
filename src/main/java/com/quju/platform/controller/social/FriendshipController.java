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
    public ApiResponse<List<Map<String, Object>>> friends() {
        return ApiResponse.ok(socialGraphService.friends(SecurityUtil.requireCurrentUserId()));
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
}
