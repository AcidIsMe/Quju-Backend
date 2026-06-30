package com.quju.platform.controller.activity;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.ReviewEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.ReviewMapper;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/activities/{activityId}/reviews")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ReviewController {

    private final ReviewMapper reviewMapper;
    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;

    @PostMapping
    public ApiResponse<ReviewEntity> create(@PathVariable String activityId,
                                            @RequestBody Map<String, String> body) {
        String userId = SecurityUtil.requireCurrentUserId();

        // 校验活动存在且已结束
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (activity.getEndTime() == null || activity.getEndTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(40912, "活动尚未结束，暂不能评价");
        }

        // 校验评价时间：活动结束后7天内可评价
        if (activity.getEndTime().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new BusinessException(40913, "评价时间已过，活动结束后7天内可评价");
        }

        // 校验用户已签到
        RegistrationEntity registration = registrationMapper.selectOne(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, activityId)
                .eq(RegistrationEntity::getUserId, userId)
                .ne(RegistrationEntity::getStatus, "cancelled"));
        if (registration == null) {
            throw new BusinessException(40302, "您未报名该活动，无法评价");
        }
        if (!"checked_in".equals(registration.getStatus())) {
            throw new BusinessException(40305, "您尚未签到，无法评价");
        }

        // 校验内容不能为空
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new BusinessException(40015, "评价内容不能为空");
        }

        ReviewEntity review = new ReviewEntity();
        review.setActivityId(activityId);
        review.setUserId(userId);
        review.setContent(content);
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
