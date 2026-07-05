package com.quju.platform.controller.social;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.service.TeamMomentService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/teams/{teamId}/moments")
@RequiredArgsConstructor
public class TeamMomentController {

    private final TeamMomentService momentService;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@PathVariable String teamId,
                                                   @RequestBody Map<String, String> body) {
        return ApiResponse.ok(momentService.create(
                teamId,
                SecurityUtil.requireCurrentUserId(),
                body.get("content"),
                body.get("image_url")
        ));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable String teamId,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false, defaultValue = "20") int limit) {
        return ApiResponse.ok(momentService.list(teamId, cursor, limit));
    }

    @DeleteMapping("/{momentId}")
    public ApiResponse<Void> delete(@PathVariable String teamId,
                                    @PathVariable String momentId) {
        momentService.delete(momentId, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{momentId}/feature")
    public ApiResponse<Void> feature(@PathVariable String teamId,
                                     @PathVariable String momentId) {
        momentService.feature(momentId, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{momentId}/unfeature")
    public ApiResponse<Void> unfeature(@PathVariable String teamId,
                                       @PathVariable String momentId) {
        momentService.unfeature(momentId, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }
}
