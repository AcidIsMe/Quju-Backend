package com.quju.platform.controller.activity;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.common.PageQuery;
import com.quju.platform.dto.common.PageResult;
import com.quju.platform.entity.ReviewEntity;
import com.quju.platform.service.ReviewService;
import com.quju.platform.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/activities/{activityId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ApiResponse<ReviewEntity> create(@PathVariable String activityId,
                                            @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        Integer rating = body.get("rating") != null ? ((Number) body.get("rating")).intValue() : null;
        return ApiResponse.ok(reviewService.create(activityId, SecurityUtil.requireCurrentUserId(), content, rating));
    }

    @GetMapping
    public ApiResponse<PageResult<Map<String, Object>>> list(@PathVariable String activityId,
                                                              @Valid PageQuery pageQuery) {
        return ApiResponse.ok(reviewService.list(activityId, pageQuery.getCursor(), pageQuery.normalizedLimit()));
    }
}
