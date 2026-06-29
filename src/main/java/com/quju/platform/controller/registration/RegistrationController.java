package com.quju.platform.controller.registration;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.service.RegistrationService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/activities/{activityId}")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@PathVariable String activityId,
                                                     @RequestHeader(value = "X-User-Id", required = false) String userId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> formData = extractFormData(body);
        return ApiResponse.ok(registrationService.register(activityId, SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId), formData));
    }

    @PostMapping("/cancel-registration")
    public ApiResponse<Void> cancel(@PathVariable String activityId,
                                    @RequestHeader(value = "X-User-Id", required = false) String userId) {
        registrationService.cancel(activityId, SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId));
        return ApiResponse.ok("已取消报名", null);
    }

    private Map<String, Object> extractFormData(Map<String, Object> body) {
        if (body == null) {
            return Map.of();
        }
        Object value = body.get("form_data");
        if (value instanceof Map<?, ?> rawMap) {
            return rawMap.entrySet().stream()
                    .filter(entry -> entry.getKey() instanceof String)
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> (String) entry.getKey(),
                            Map.Entry::getValue));
        }
        return body;
    }
}
