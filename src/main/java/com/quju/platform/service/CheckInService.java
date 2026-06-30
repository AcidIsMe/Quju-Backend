package com.quju.platform.service;

import com.quju.platform.dto.registration.CheckInReq;

import java.util.Map;

public interface CheckInService {
    void checkIn(String activityId, String userId, CheckInReq req);
    Map<String, Object> qrcode(String activityId, String userId);
    Map<String, Object> list(String activityId, String userId);
}
