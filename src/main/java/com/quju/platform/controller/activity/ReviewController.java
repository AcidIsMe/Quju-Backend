package com.quju.platform.controller.activity;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.ReviewEntity;
import com.quju.platform.mapper.ReviewMapper;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/activities/{activityId}/reviews")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ReviewController {

    private final ReviewMapper reviewMapper;

    @PostMapping
    public ApiResponse<ReviewEntity> create(@PathVariable String activityId,
                                            @RequestHeader(value = "X-User-Id", required = false) String userId,
                                            @RequestBody Map<String, String> body) {
        ReviewEntity review = new ReviewEntity();
        review.setActivityId(activityId);
        review.setUserId(SecurityUtil.currentUserIdOr(userId == null ? "dev-user" : userId));
        review.setContent(body.get("content"));
        reviewMapper.insert(review);
        return ApiResponse.ok(review);
    }

    @GetMapping
    public ApiResponse<List<ReviewEntity>> list(@PathVariable String activityId) {
        return ApiResponse.ok(reviewMapper.selectList(Wrappers.<ReviewEntity>lambdaQuery()
                .eq(ReviewEntity::getActivityId, activityId)
                .orderByDesc(ReviewEntity::getCreatedAt)));
    }
}
