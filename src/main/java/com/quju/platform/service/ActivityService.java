package com.quju.platform.service;

import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;

import java.util.List;

public interface ActivityService {
    ActivityEntity create(ActivityCreateReq req, String creatorId);
    ActivityEntity update(String id, ActivityCreateReq req, String userId);
    ActivityEntity detail(String id);
    ActivityEntity cloneActivity(String id, String creatorId);
    void deleteDraft(String id, String userId);
    List<RegistrationEntity> participants(String id);
    ActivityEntity submitForReview(String id);
}
