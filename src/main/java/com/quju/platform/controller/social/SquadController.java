package com.quju.platform.controller.social;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.dto.social.SquadPointsRankResp;
import com.quju.platform.entity.TeamEntity;
import com.quju.platform.entity.TeamJoinRequestEntity;
import com.quju.platform.mapper.ImMessageMapper;
import com.quju.platform.entity.ImMessageEntity;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.service.SquadService;
import com.quju.platform.service.TeamAnnouncementService;
import com.quju.platform.util.SecurityUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class SquadController {

    private final SquadService squadService;
    private final TeamAnnouncementService teamAnnouncementService;
    private final ImMessageMapper imMessageMapper;
    private final UserMapper userMapper;

    @PostMapping
    public ApiResponse<TeamEntity> create(@Valid @RequestBody SquadCreateReq req) {
        return ApiResponse.ok(squadService.create(req, SecurityUtil.requireCurrentUserId()));
    }

    @GetMapping
    public ApiResponse<List<TeamEntity>> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false, name = "interest_tags") String interestTags,
                                              @RequestParam(required = false, defaultValue = "newest") String sort,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(squadService.list(q, interestTags, sort, cursor, limit));
    }

    @GetMapping("/recommended")
    public ApiResponse<List<Map<String, Object>>> recommended(@RequestParam(required = false, defaultValue = "10") Integer limit) {
        int effectiveLimit = limit == null ? 10 : Math.min(limit, 50);
        return ApiResponse.ok(squadService.recommend(SecurityUtil.requireCurrentUserId(), effectiveLimit));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.ok(squadService.detailWithMembers(id, SecurityUtil.getCurrentUserIdOrNull()));
    }

    @PostMapping("/{id}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable String id) {
        return ApiResponse.ok(squadService.join(id, SecurityUtil.requireCurrentUserId()));
    }

    @PostMapping("/{id}/dissolve")
    public ApiResponse<Void> dissolve(@PathVariable String id) {
        squadService.dissolve(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public ApiResponse<TeamEntity> update(@PathVariable String id,
                                          @RequestBody SquadCreateReq req) {
        return ApiResponse.ok(squadService.update(id, SecurityUtil.requireCurrentUserId(), req));
    }

    @PostMapping("/{id}/leave")
    public ApiResponse<Void> leave(@PathVariable String id) {
        squadService.leave(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/members/{targetUserId}/role")
    public ApiResponse<Void> changeRole(@PathVariable String id,
                                        @PathVariable String targetUserId,
                                        @RequestBody Map<String, String> body) {
        squadService.changeRole(id, SecurityUtil.requireCurrentUserId(), targetUserId, body.get("role"));
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/members/{targetUserId}")
    public ApiResponse<Void> removeMember(@PathVariable String id,
                                          @PathVariable String targetUserId) {
        squadService.removeMember(id, SecurityUtil.requireCurrentUserId(), targetUserId);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/members")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable String id) {
        return ApiResponse.ok(squadService.members(id));
    }

    @GetMapping("/{id}/join-requests")
    public ApiResponse<List<TeamJoinRequestEntity>> joinRequests(@PathVariable String id) {
        return ApiResponse.ok(squadService.joinRequests(id, SecurityUtil.requireCurrentUserId()));
    }

    @PostMapping("/{id}/join-requests/{requestId}/approve")
    public ApiResponse<Void> approveRequest(@PathVariable String id,
                                            @PathVariable String requestId) {
        squadService.approveRequest(id, SecurityUtil.requireCurrentUserId(), requestId);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/join-requests/{requestId}/reject")
    public ApiResponse<Void> rejectRequest(@PathVariable String id,
                                           @PathVariable String requestId) {
        squadService.rejectRequest(id, SecurityUtil.requireCurrentUserId(), requestId);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/leaderboard")
    public ApiResponse<List<SquadPointsRankResp>> leaderboard(@PathVariable String id) {
        return ApiResponse.ok(squadService.leaderboard(id));
    }

    @PostMapping("/{id}/points")
    public ApiResponse<Void> addPoints(@PathVariable String id,
                                       @RequestBody Map<String, Object> body) {
        String targetUserId = (String) body.get("user_id");
        int points = body.get("points") instanceof Integer ? (Integer) body.get("points") : 0;
        squadService.addPoints(id, targetUserId, points);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/transfer-leader")
    public ApiResponse<Void> transferLeader(@PathVariable String id,
                                            @RequestBody Map<String, String> body) {
        squadService.transferLeader(id, SecurityUtil.requireCurrentUserId(), body.get("new_leader_id"));
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/blacklist")
    public ApiResponse<Void> addToBlacklist(@PathVariable String id,
                                            @RequestBody Map<String, String> body) {
        squadService.addToBlacklist(id, SecurityUtil.requireCurrentUserId(), body.get("user_id"));
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/blacklist/{targetUserId}")
    public ApiResponse<Void> removeFromBlacklist(@PathVariable String id,
                                                  @PathVariable String targetUserId) {
        squadService.removeFromBlacklist(id, SecurityUtil.requireCurrentUserId(), targetUserId);
        return ApiResponse.ok();
    }

    // ========== 小队公告 ==========

    @PostMapping("/{id}/announcements")
    public ApiResponse<Void> createAnnouncement(@PathVariable String id,
                                                @RequestBody Map<String, String> body) {
        teamAnnouncementService.create(id, SecurityUtil.requireCurrentUserId(), body.get("content"));
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/announcements")
    public ApiResponse<List<Map<String, Object>>> listAnnouncements(@PathVariable String id) {
        return ApiResponse.ok(teamAnnouncementService.list(id));
    }

    @DeleteMapping("/{id}/announcements/{aid}")
    public ApiResponse<Void> deleteAnnouncement(@PathVariable String id,
                                                @PathVariable String aid) {
        teamAnnouncementService.delete(id, SecurityUtil.requireCurrentUserId(), aid);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/announcements/{aid}/pin")
    public ApiResponse<Void> togglePinAnnouncement(@PathVariable String id,
                                                    @PathVariable String aid) {
        teamAnnouncementService.togglePin(id, SecurityUtil.requireCurrentUserId(), aid);
        return ApiResponse.ok();
    }

    // ========== 群文件 ==========

    @GetMapping("/{id}/chat/files")
    public ApiResponse<List<Map<String, Object>>> chatFiles(@PathVariable String id) {
        String entityId = "team:" + id;
        List<Map<String, Object>> files = imMessageMapper.selectList(
                Wrappers.<ImMessageEntity>lambdaQuery()
                        .eq(ImMessageEntity::getEntityType, "group")
                        .eq(ImMessageEntity::getEntityId, entityId)
                        .in(ImMessageEntity::getType, java.util.List.of("image", "file"))
                        .orderByDesc(ImMessageEntity::getCreatedAt)
        ).stream().map(msg -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("id", msg.getId());
            item.put("type", msg.getType());
            item.put("content", msg.getContent());
            item.put("sender_id", msg.getSenderId());
            UserEntity sender = userMapper.selectById(msg.getSenderId());
            item.put("sender_nickname", sender != null ? sender.getNickname() : "成员");
            item.put("created_at", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : "");
            return item;
        }).toList();
        return ApiResponse.ok(files);
    }
}