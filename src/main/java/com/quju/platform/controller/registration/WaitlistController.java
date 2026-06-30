package com.quju.platform.controller.registration;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.WaitlistEntity;
import com.quju.platform.service.RegistrationService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/activities/{activityId}")
@RequiredArgsConstructor
public class WaitlistController {

    private final RegistrationService registrationService;

    @GetMapping("/waitlist-position")
    public ApiResponse<Map<String, Object>> position(@PathVariable String activityId) {
        return ApiResponse.ok(registrationService.getWaitlistPosition(activityId, SecurityUtil.requireCurrentUserId()));
    }

    @PostMapping("/join-waitlist")
    public ApiResponse<Map<String, Object>> join(@PathVariable String activityId) {
        WaitlistEntity item = registrationService.joinWaitlist(activityId, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok(Map.of("position", item.getPosition(), "waiting_count_ahead", Math.max(0, item.getPosition() - 1)));
    }

    @DeleteMapping("/leave-waitlist")
    public ApiResponse<Void> leave(@PathVariable String activityId) {
        registrationService.leaveWaitlist(activityId, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }
}
