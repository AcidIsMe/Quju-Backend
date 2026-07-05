package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.common.PageResult;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.ReviewEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.ReviewMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;
    private final UserMapper userMapper;

    @Override
    public ReviewEntity create(String activityId, String userId, String content, Integer rating) {
        // 校验内容不能为空
        if (content == null || content.isBlank()) {
            throw new BusinessException(40015, "评价内容不能为空");
        }

        // 校验活动存在
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }

        // 已签到且在期限内：活动已结束才可评价
        if (activity.getEndTime() == null || activity.getEndTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(40912, "活动尚未结束，暂不能评价");
        }

        // 超时关闭入口：活动结束后7天内可评价
        if (activity.getEndTime().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new BusinessException(40913, "评价时间已过，活动结束后7天内可评价");
        }

        // 校验用户已报名（且未取消）
        RegistrationEntity registration = registrationMapper.selectOne(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, activityId)
                .eq(RegistrationEntity::getUserId, userId)
                .ne(RegistrationEntity::getStatus, "cancelled"));
        if (registration == null) {
            throw new BusinessException(40302, "您未报名该活动，无法评价");
        }

        // 未签到不可评价
        if (!"checked_in".equals(registration.getStatus())) {
            throw new BusinessException(40305, "您尚未签到，无法评价");
        }

        ReviewEntity review = new ReviewEntity();
        review.setActivityId(activityId);
        review.setUserId(userId);
        review.setContent(content);
        review.setRating(rating);
        review.setCreatedAt(LocalDateTime.now());
        reviewMapper.insert(review);
        return review;
    }

    @Override
    public PageResult<Map<String, Object>> list(String activityId, String cursor, int limit) {
        // 查询 limit + 1 条以判断是否还有更多
        List<ReviewEntity> reviews = reviewMapper.selectList(
                Wrappers.<ReviewEntity>lambdaQuery()
                        .eq(ReviewEntity::getActivityId, activityId)
                        .lt(cursor != null && !cursor.isBlank(),
                                ReviewEntity::getCreatedAt,
                                cursor != null && !cursor.isBlank() ? LocalDateTime.parse(cursor) : null)
                        .orderByDesc(ReviewEntity::getCreatedAt)
                        .last("LIMIT " + (limit + 1))
        );

        boolean hasMore = reviews.size() > limit;
        if (hasMore) {
            reviews = reviews.subList(0, limit);
        }

        List<Map<String, Object>> records = reviews.stream().map(review -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", review.getId());
            map.put("activity_id", review.getActivityId());
            map.put("user_id", review.getUserId());
            map.put("content", review.getContent());
            map.put("rating", review.getRating());
            map.put("created_at", review.getCreatedAt() != null
                    ? review.getCreatedAt().toString() : null);

            // 查询用户昵称和头像
            UserEntity user = userMapper.selectById(review.getUserId());
            if (user != null) {
                map.put("nickname", user.getNickname());
                map.put("avatar_url", user.getAvatarUrl());
            } else {
                map.put("nickname", null);
                map.put("avatar_url", null);
            }
            return map;
        }).collect(Collectors.toList());

        String nextCursor = "";
        if (hasMore && !records.isEmpty()) {
            nextCursor = (String) records.get(records.size() - 1).get("created_at");
        }

        return new PageResult<>(records, Map.of(
                "has_more", hasMore,
                "limit", limit,
                "next_cursor", nextCursor
        ));
    }
}
