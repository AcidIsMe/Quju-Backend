package com.quju.platform.service;

import com.quju.platform.dto.common.PageResult;
import com.quju.platform.entity.ReviewEntity;

import java.util.Map;

public interface ReviewService {
    ReviewEntity create(String activityId, String userId, String content);
    PageResult<Map<String, Object>> list(String activityId, String cursor, int limit);
}
