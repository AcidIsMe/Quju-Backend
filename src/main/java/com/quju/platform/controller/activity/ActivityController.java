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

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping
    public ApiResponse<ActivityEntity> create(@Valid @RequestBody ActivityCreateReq req,
                                              @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.ok(activityService.create(req, SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActivityEntity> update(@PathVariable String id, @Valid @RequestBody ActivityCreateReq req,
                                              @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.ok(activityService.update(id, req, SecurityUtil.currentUserIdOr(userId)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityEntity> detail(@PathVariable String id) {
        return ApiResponse.ok(activityService.detail(id));
    }

    @PostMapping("/{id}/clone")
    public ApiResponse<ActivityEntity> cloneActivity(@PathVariable String id,
                                                     @RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.ok(activityService.cloneActivity(id, SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, @RequestHeader(value = "X-User-Id", required = false) String userId) {
        activityService.deleteDraft(id, SecurityUtil.currentUserIdOr(userId));
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
