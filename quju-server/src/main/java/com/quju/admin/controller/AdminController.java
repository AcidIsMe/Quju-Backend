package com.quju.admin.controller;

import com.quju.admin.dto.BanRequest;
import com.quju.admin.service.AdminService;
import com.quju.common.ApiResponse;
import com.quju.common.AuthUtil;
import com.quju.auth.service.BizException;
import com.quju.common.ErrorCode;
import com.quju.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 验证管理员权限 */
    private User checkAdmin(HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        if (!"admin".equals(user.getRole())) {
            throw new BizException(40300, "无管理员权限");
        }
        return user;
    }

    /** 用户查询 */
    @GetMapping("/users")
    public ApiResponse<List<Map<String, Object>>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        checkAdmin(request);
        return ApiResponse.success(adminService.listUsers(q, role, status, cursor, limit));
    }

    /** 用户详情 */
    @GetMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> getUserDetail(@PathVariable String id,
                                                           HttpServletRequest request) {
        checkAdmin(request);
        return ApiResponse.success(adminService.getUserDetail(id));
    }

    /** 封禁用户 */
    @PostMapping("/users/{id}/ban")
    public ApiResponse<Void> banUser(@PathVariable String id,
                                      @Valid @RequestBody BanRequest req,
                                      HttpServletRequest request) {
        User admin = checkAdmin(request);
        adminService.banUser(id, req, admin);
        return ApiResponse.success("用户已封禁", null);
    }

    /** 解封用户 */
    @PostMapping("/users/{id}/unban")
    public ApiResponse<Void> unbanUser(@PathVariable String id,
                                        HttpServletRequest request) {
        User admin = checkAdmin(request);
        adminService.unbanUser(id, admin);
        return ApiResponse.success("用户已解封", null);
    }
}
