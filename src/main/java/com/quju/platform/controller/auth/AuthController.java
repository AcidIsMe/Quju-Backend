package com.quju.platform.controller.auth;

import com.quju.platform.dto.auth.LoginReq;
import com.quju.platform.dto.auth.MerchantApplyReq;
import com.quju.platform.dto.auth.RegisterReq;
import com.quju.platform.dto.auth.TokenRefreshReq;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register/personal")
    public ApiResponse<Map<String, Object>> registerPersonal(@Valid @RequestBody RegisterReq req) {
        return ApiResponse.ok("注册成功，请前往邮箱激活账号", authService.registerPersonal(req));
    }

    @PostMapping("/register/merchant")
    public ApiResponse<Map<String, Object>> registerMerchant(@Valid @RequestBody MerchantApplyReq req) {
        return ApiResponse.ok("资料已提交，请等待审核", authService.registerMerchant(req));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginReq req, HttpServletRequest request) {
        return ApiResponse.ok(authService.login(req, request.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refresh(@Valid @RequestBody TokenRefreshReq req) {
        return ApiResponse.ok(authService.refresh(req.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) TokenRefreshReq req) {
        if (req != null) {
            authService.logout(req.getRefreshToken());
        }
        return ApiResponse.ok("已退出登录", null);
    }

    @GetMapping({"/activate/{token}", "/activate"})
    public ApiResponse<Void> activate(@PathVariable(required = false) String token, @RequestParam(value = "token", required = false) String tokenParam) {
        authService.activate(token == null ? tokenParam : token);
        return ApiResponse.ok("账号激活成功，请登录", null);
    }

    @PostMapping("/resend-activation")
    public ApiResponse<Void> resendActivation(@RequestBody Map<String, String> body) {
        authService.resendActivation(body.get("email"));
        return ApiResponse.ok("激活邮件已重新发送", null);
    }
}
