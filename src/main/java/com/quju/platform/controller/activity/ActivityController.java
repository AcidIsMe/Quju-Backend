package com.quju.platform.controller.activity;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.service.ActivityService;
import com.quju.platform.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping
    public ApiResponse<ActivityEntity> create(@Valid @RequestBody ActivityCreateReq req) {
        return ApiResponse.ok(activityService.create(req, SecurityUtil.requireCurrentUserId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActivityEntity> update(@PathVariable String id, @Valid @RequestBody ActivityCreateReq req) {
        return ApiResponse.ok(activityService.update(id, req, SecurityUtil.requireCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String id) {
        return ApiResponse.ok(activityService.detailWithAggregation(id, SecurityUtil.getCurrentUserIdOrNull()));
    }

    @PostMapping("/{id}/clone")
    public ApiResponse<ActivityEntity> cloneActivity(@PathVariable String id) {
        return ApiResponse.ok(activityService.cloneActivity(id, SecurityUtil.requireCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        activityService.deleteDraft(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/participants")
    public ApiResponse<List<RegistrationEntity>> participants(@PathVariable String id) {
        return ApiResponse.ok(activityService.participants(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ActivityEntity> submit(@PathVariable String id) {
        return ApiResponse.ok(activityService.submitForReview(id));
    }
}
