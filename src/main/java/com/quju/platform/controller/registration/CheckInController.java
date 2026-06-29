package com.quju.platform.controller.registration;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.registration.CheckInReq;
import com.quju.platform.service.CheckInService;
import com.quju.platform.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/activities/{activityId}/check-in")
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping
    public ApiResponse<Void> checkIn(@PathVariable String activityId,
                                     @RequestHeader(value = "X-User-Id", required = false) String userId,
                                     @Valid @RequestBody CheckInReq req) {
        checkInService.checkIn(activityId, SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId), req);
        return ApiResponse.ok("签到成功", null);
    }

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable String activityId) {
        return ApiResponse.ok(checkInService.list(activityId));
    }

    @PostMapping("/qrcode")
    public ApiResponse<Map<String, Object>> qrcode(@PathVariable String activityId) {
        return ApiResponse.ok(checkInService.qrcode(activityId));
    }
}
