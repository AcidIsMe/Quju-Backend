package com.quju.auth.controller;

import com.quju.auth.dto.LoginRequest;
import com.quju.auth.dto.RefreshRequest;
import com.quju.auth.dto.RegisterRequest;
import com.quju.auth.dto.ResendActivationRequest;
import com.quju.auth.service.AuthService;
import com.quju.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 个人用户注册 — 开发环境返回 activation_token 以便直接激活 */
    @PostMapping("/register/personal")
    public ApiResponse<Map<String, Object>> registerPersonal(@Valid @RequestBody RegisterRequest req) {
        Map<String, Object> result = authService.register(req);
        return ApiResponse.success("注册成功，请前往邮箱激活账号", result);
    }

    /** 用户登录 */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        Map<String, Object> result = authService.login(req);
        return ApiResponse.success(result);
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.getRefreshToken());
        return ApiResponse.success("已退出登录", null);
    }

    /** 刷新 Token */
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest req) {
        Map<String, Object> result = authService.refresh(req.getRefreshToken());
        return ApiResponse.success(result);
    }

    /** 激活邮箱 */
    @GetMapping("/activate/{token}")
    public ApiResponse<Void> activate(@PathVariable String token) {
        authService.activate(token);
        return ApiResponse.success("账号激活成功，请登录", null);
    }

    /** 重发激活邮件 */
    @PostMapping("/resend-activation")
    public ApiResponse<Void> resendActivation(@Valid @RequestBody ResendActivationRequest req) {
        authService.resendActivation(req.getEmail());
        return ApiResponse.success("激活邮件已重新发送", null);
    }
}
