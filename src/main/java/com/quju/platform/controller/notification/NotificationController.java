package com.quju.platform.controller.notification;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.common.PageResult;
import com.quju.platform.entity.NotificationEntity;
import com.quju.platform.service.NotificationService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<PageResult<NotificationEntity>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") Integer limit) {
        int size = Math.max(1, Math.min(limit == null ? 20 : limit, 100));
        return ApiResponse.ok(notificationService.listPage(
                SecurityUtil.requireCurrentUserId(), type, isRead, cursor, size));
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

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll() {
        notificationService.readAll(SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }
}
