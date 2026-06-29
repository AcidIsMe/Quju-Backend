package com.quju.activity.controller;

import com.quju.activity.dto.CreateActivityRequest;
import com.quju.activity.service.ActivityService;
import com.quju.common.ApiResponse;
import com.quju.common.AuthUtil;
import com.quju.entity.Activity;
import com.quju.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /** 创建活动 */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateActivityRequest req,
                                                    HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        Activity activity = activityService.create(user, req);
        return ApiResponse.success(Map.of(
                "id", activity.getId(),
                "status", activity.getStatus(),
                "message", activity.getStatus().startsWith("pending") ? "活动已提交，请等待审核" : "草稿已保存"
        ));
    }

    /** 更新活动 */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable String id,
                                                    @Valid @RequestBody CreateActivityRequest req,
                                                    HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        Activity activity = activityService.update(id, user, req);
        return ApiResponse.success(Map.of(
                "id", activity.getId(),
                "status", activity.getStatus(),
                "message", activity.getStatus().startsWith("pending") ? "活动已提交，请等待审核" : "草稿已保存"
        ));
    }

    /** 获取活动详情 */
    @GetMapping("/{id}")
    public ApiResponse<Activity> getById(@PathVariable String id) {
        return ApiResponse.success(activityService.getById(id));
    }

    /** 克隆活动 */
    @PostMapping("/{id}/clone")
    public ApiResponse<Map<String, Object>> clone(@PathVariable String id,
                                                   HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        Activity activity = activityService.clone(id, user);
        return ApiResponse.success(Map.of(
                "id", activity.getId(),
                "title", activity.getTitle(),
                "status", activity.getStatus()
        ));
    }

    /** 删除草稿 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id,
                                     HttpServletRequest request) {
        User user = AuthUtil.getCurrentUser(request);
        activityService.delete(id, user);
        return ApiResponse.success("草稿已删除", null);
    }

    /** 获取报名用户列表 */
    @GetMapping("/{id}/participants")
    public ApiResponse<List<Map<String, Object>>> getParticipants(@PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(activityService.getParticipants(id, page, limit));
    }
}
