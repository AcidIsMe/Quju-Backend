package com.quju.user.controller;

import com.quju.common.ApiResponse;
import com.quju.common.AuthUtil;
import com.quju.user.dto.UpdateProfileRequest;
import com.quju.user.entity.User;
import com.quju.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public ApiResponse<User> getMe(HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        return ApiResponse.success(userService.getMe(user.getId()));
    }

    /** 更新当前用户资料 */
    @PatchMapping("/me")
    public ApiResponse<User> updateMe(@RequestBody UpdateProfileRequest req, HttpServletRequest request) {
        User currentUser = AuthUtil.getCurrentUser(request);
        return ApiResponse.success(userService.updateMe(currentUser.getId(), req));
    }

    /** 查看用户公开信息 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getPublicProfile(@PathVariable String id) {
        return ApiResponse.success(userService.getPublicProfile(id));
    }

    /** 检查昵称是否可用 */
    @GetMapping("/check-nickname")
    public ApiResponse<Map<String, Boolean>> checkNickname(@RequestParam String nickname) {
        return ApiResponse.success(Map.of("available", userService.checkNickname(nickname)));
    }

    /** 我创建的活动（后继完整实现） */
    @GetMapping("/me/created-activities")
    public ApiResponse<List<Map<String, Object>>> getCreatedActivities(HttpServletRequest request,
        @RequestParam(defaultValue = "") String cursor,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "published") String status) {
        User currentUser = AuthUtil.getCurrentUser(request);
        return ApiResponse.success(userService.getCreatedActivities(currentUser.getId()));
    }

    /** 我报名的活动（后继完整实现） */
    @GetMapping("/me/joined-activities")
    public ApiResponse<List<Map<String, Object>>> getJoinedActivities(HttpServletRequest request,
        @RequestParam(defaultValue = "") String cursor,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "registered") String status) {
        User currentUser = AuthUtil.getCurrentUser(request);
        return ApiResponse.success(userService.getJoinedActivities(currentUser.getId()));
    }
}
