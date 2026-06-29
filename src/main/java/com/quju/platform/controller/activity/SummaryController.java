package com.quju.platform.controller.activity;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.ActivitySummaryEntity;
import com.quju.platform.mapper.ActivitySummaryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/activities/{activityId}/summary")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SummaryController {

    private final ActivitySummaryMapper summaryMapper;

    @PostMapping
    public ApiResponse<ActivitySummaryEntity> create(@PathVariable String activityId, @RequestBody Map<String, Object> body) {
        ActivitySummaryEntity summary = new ActivitySummaryEntity();
        summary.setActivityId(activityId);
        summary.setContent(String.valueOf(body.getOrDefault("content", "")));
        summaryMapper.insert(summary);
        return ApiResponse.ok(summary);
    }

    @GetMapping
    public ApiResponse<ActivitySummaryEntity> detail(@PathVariable String activityId) {
        return ApiResponse.ok(summaryMapper.selectOne(Wrappers.<ActivitySummaryEntity>lambdaQuery()
                .eq(ActivitySummaryEntity::getActivityId, activityId)));
    }
}
