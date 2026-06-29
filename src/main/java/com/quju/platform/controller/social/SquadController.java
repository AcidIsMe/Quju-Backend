package com.quju.platform.controller.social;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.social.SquadCreateReq;
import com.quju.platform.entity.TeamEntity;
import com.quju.platform.service.SquadService;
import com.quju.platform.util.SecurityUtil;
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

    @PostMapping
    public ApiResponse<TeamEntity> create(@Valid @RequestBody SquadCreateReq req,
                                          @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.ok(squadService.create(req, SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId)));
    }

    @GetMapping
    public ApiResponse<List<TeamEntity>> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(squadService.list(q, limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<TeamEntity> detail(@PathVariable String id) {
        return ApiResponse.ok(squadService.detail(id));
    }

    @PostMapping("/{id}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable String id,
                                                 @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.ok(squadService.join(id, SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId)));
    }

    @PostMapping("/{id}/dissolve")
    public ApiResponse<Void> dissolve(@PathVariable String id,
                                      @RequestHeader(value = "X-User-Id", required = false) String userId) {
        squadService.dissolve(id, SecurityUtil.currentUserIdOr(userId));
        return ApiResponse.ok();
    }
}
