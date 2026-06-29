package com.quju.registration.controller;

import com.quju.common.ApiResponse;
import com.quju.common.AuthUtil;
import com.quju.entity.Registration;
import com.quju.registration.service.RegistrationService;
import com.quju.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** 报名活动 */
    @PostMapping("/{id}/register")
    public ApiResponse<Map<String, Object>> register(@PathVariable String id,
                                                      @RequestBody(required = false) Map<String, Object> body,
                                                      HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        String formData = body != null && body.get("form_data") != null
                ? body.get("form_data").toString() : null;
        Registration reg = registrationService.register(id, user, formData);
        return ApiResponse.success(Map.of(
                "registration_id", reg.getId(),
                "status", reg.getStatus()
        ));
    }

    /** 取消报名 */
    @PostMapping("/{id}/cancel-registration")
    public ApiResponse<Void> cancel(@PathVariable String id,
                                     HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        registrationService.cancel(id, user);
        return ApiResponse.success("已取消报名", null);
    }

    /** 加入等待队列 */
    @PostMapping("/{id}/join-waitlist")
    public ApiResponse<Map<String, Object>> joinWaitlist(@PathVariable String id,
                                                          HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        Map<String, Object> result = registrationService.joinWaitlist(id, user);
        return ApiResponse.success(result);
    }

    /** 退出等待队列 */
    @DeleteMapping("/{id}/leave-waitlist")
    public ApiResponse<Void> leaveWaitlist(@PathVariable String id,
                                            HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        registrationService.leaveWaitlist(id, user);
        return ApiResponse.success("已退出等待队列", null);
    }
}
