package com.quju.platform.service;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.entity.ActivityEntity;

import java.util.List;
import java.util.Map;

public interface ActivityService {
    ActivityEntity create(ActivityCreateReq req, String creatorId);
    ActivityEntity update(String id, ActivityCreateReq req, String userId);
    ActivityEntity detail(String id);
    Map<String, Object> detailWithAggregation(String id, String currentUserId);
    ActivityEntity cloneActivity(String id, String creatorId);
    void deleteDraft(String id, String userId);
    List<Map<String, Object>> participants(String id);
    ActivityEntity submitForReview(String id);
    ActivityEntity processAiReview(String id);
}
