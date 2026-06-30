package com.quju.platform.controller.notification;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.NotificationEntity;
import com.quju.platform.service.NotificationService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationEntity>> list() {
        return ApiResponse.ok(notificationService.list(SecurityUtil.requireCurrentUserId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount() {
        long count = notificationService.unreadCount(SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok(Map.of("count", count));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable String id) {
        notificationService.markRead(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }
}
