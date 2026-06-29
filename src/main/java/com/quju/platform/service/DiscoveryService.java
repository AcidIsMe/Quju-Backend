package com.quju.platform.service;

import com.quju.platform.dto.activity.ActivityQueryReq;
import com.quju.platform.entity.ActivityEntity;

import java.util.List;

public interface DiscoveryService {
    List<ActivityEntity> latest(ActivityQueryReq req);
    List<ActivityEntity> recommended(ActivityQueryReq req);
    List<ActivityEntity> search(ActivityQueryReq req);
    List<ActivityEntity> nearby(ActivityQueryReq req);
}
