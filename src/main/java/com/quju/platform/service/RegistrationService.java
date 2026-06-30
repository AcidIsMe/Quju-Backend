package com.quju.platform.service;

import com.quju.platform.entity.WaitlistEntity;

import java.util.Map;

public interface RegistrationService {
    Map<String, Object> register(String activityId, String userId, Map<String, Object> formData);
    void cancel(String activityId, String userId);
    WaitlistEntity joinWaitlist(String activityId, String userId);
    void leaveWaitlist(String activityId, String userId);
    Map<String, Object> getWaitlistPosition(String activityId, String userId);
}
