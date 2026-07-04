package com.quju.platform.controller.im;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.ImMessageEntity;
import com.quju.platform.service.ImService;
import com.quju.platform.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/im")
@RequiredArgsConstructor
public class ImController {

    private final ImService imService;

    @PostMapping("/messages")
    public ApiResponse<ImMessageEntity> send(@Valid @RequestBody ImMessageDto dto) {
        return ApiResponse.ok(imService.send(dto, SecurityUtil.requireCurrentUserId()));
    }

    @PostMapping("/messages/{id}/recall")
    public ApiResponse<Void> recall(@PathVariable String id) {
        imService.recall(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    /**
     * 获取消息历史（游标分页，按时间倒序）
     */
    @GetMapping("/messages")
    public ApiResponse<CursorPage<ImMessageEntity>> getMessages(
            @RequestParam("entity_type") String entityType,
            @RequestParam("entity_id") String entityId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        CursorPage<ImMessageEntity> page = imService.getMessages(entityType, entityId, cursor, limit);
        return ApiResponse.ok(page);
    }

    /**
     * 获取会话列表（按最后消息时间倒序）
     */
    @GetMapping("/conversations")
    public ApiResponse<List<Map<String, Object>>> getConversations() {
        return ApiResponse.ok(imService.getConversations(SecurityUtil.requireCurrentUserId()));
    }

    /**
     * 将指定会话中对方发送的消息全部标记为已读
     */
    @PostMapping("/messages/read")
    public ApiResponse<Void> markRead(
            @RequestParam("entity_type") String entityType,
            @RequestParam("entity_id") String entityId) {
        imService.markRead(entityType, entityId, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    /**
     * 获取指定会话的未读消息数
     */
    @GetMapping("/messages/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadCount(
            @RequestParam("entity_type") String entityType,
            @RequestParam("entity_id") String entityId) {
        long count = imService.getUnreadCount(entityType, entityId, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok(Map.of("count", count));
    }

    /**
     * 获取总未读消息数
     */
    @GetMapping("/messages/total-unread")
    public ApiResponse<Map<String, Long>> getTotalUnread() {
        long count = imService.getTotalUnreadCount(SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok(Map.of("count", count));
    }

    /**
     * 转发消息到目标会话
     */
    @PostMapping("/messages/{id}/forward")
    public ApiResponse<ImMessageEntity> forward(@PathVariable String id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(imService.forward(id, SecurityUtil.requireCurrentUserId(),
                body.get("target_entity_type"), body.get("target_entity_id")));
    }
}
