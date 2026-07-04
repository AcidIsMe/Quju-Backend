package com.quju.platform.controller.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.platform.config.QujuProperties;
import com.quju.platform.dto.auth.ChangePasswordReq;
import com.quju.platform.dto.auth.LoginReq;
import com.quju.platform.dto.auth.MerchantApplyReq;
import com.quju.platform.dto.auth.RegisterReq;
import com.quju.platform.dto.auth.TokenRefreshReq;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.service.AuthService;
import com.quju.platform.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final QujuProperties qujuProperties;
    private final ObjectMapper objectMapper;

    @PostMapping("/register/personal")
    public ApiResponse<Map<String, Object>> registerPersonal(@Valid @RequestBody RegisterReq req) {
        return ApiResponse.ok("注册成功，请前往邮箱激活账号", authService.registerPersonal(req));
    }

    @PostMapping(value = "/register/merchant", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> registerMerchant(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String nickname,
            @RequestParam("merchant_name") String merchantName,
            @RequestParam(value = "activity_domains", required = false) String activityDomainsJson,
            @RequestParam("license_image") MultipartFile licenseImage) throws Exception {

        // 手动校验（multipart 无法使用 @Valid @RequestBody）
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new BusinessException(40000, "邮箱格式不正确");
        }
        if (password == null || password.isBlank() || password.length() < 8 || password.length() > 72) {
            throw new BusinessException(40000, "密码长度需要 8-72 位");
        }
        if (nickname == null || nickname.isBlank()) {
            throw new BusinessException(40000, "昵称不能为空");
        }
        if (merchantName == null || merchantName.isBlank()) {
            throw new BusinessException(40000, "商家名称不能为空");
        }

        // 保存营业执照图片
        Path dir = Path.of(qujuProperties.getFiles().getUploadDir(), "license").toAbsolutePath();
        Files.createDirectories(dir);
        String originalFilename = Objects.requireNonNullElse(licenseImage.getOriginalFilename(), "license.bin");
        String filename = UUID.randomUUID() + "-" + originalFilename;
        Path target = dir.resolve(filename);
        licenseImage.transferTo(target);
        String licenseImageUrl = "/uploads/license/" + filename;

        // 解析 activity_domains（前端以 JSON 字符串传递，如 '["运动","音乐"]'）
        List<String> activityDomains = List.of();
        if (activityDomainsJson != null && !activityDomainsJson.isBlank()) {
            try {
                activityDomains = objectMapper.readValue(activityDomainsJson, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // JSON 解析失败时忽略，使用空列表
            }
        }

        MerchantApplyReq req = new MerchantApplyReq();
        req.setEmail(email);
        req.setPassword(password);
        req.setNickname(nickname);
        req.setMerchantName(merchantName);
        req.setActivityDomains(activityDomains);
        req.setLicenseImageUrl(licenseImageUrl);

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

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        String userId = SecurityUtil.requireCurrentUserId();
        authService.changePassword(userId, req);
        return ApiResponse.ok("密码修改成功", null);
    }
}
