package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.quju.platform.dto.activity.ActivityQueryReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DiscoveryServiceImpl implements DiscoveryService {

    private final ActivityMapper activityMapper;

    @Override
    public List<ActivityEntity> latest(ActivityQueryReq req) {
        return activityMapper.selectPage(new Page<>(1, req.normalizedLimit()), Wrappers.<ActivityEntity>lambdaQuery()
                .eq(req.getStatus() != null, ActivityEntity::getStatus, req.getStatus())
                .eq(req.getStatus() == null, ActivityEntity::getStatus, "published")
                .eq(req.getCity() != null && !req.getCity().isBlank(), ActivityEntity::getCity, req.getCity())
                .eq(req.getFeeType() != null && !req.getFeeType().isBlank(), ActivityEntity::getFeeType, req.getFeeType())
                .orderByDesc(ActivityEntity::getCreatedAt)).getRecords();
    }

    @Override
    public List<ActivityEntity> recommended(ActivityQueryReq req) {
        return latest(req);
    }

    @Override
    public List<ActivityEntity> search(ActivityQueryReq req) {
        return activityMapper.selectPage(new Page<>(1, req.normalizedLimit()), Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getStatus, "published")
                .and(req.getQ() != null && !req.getQ().isBlank(), wrapper -> wrapper
                        .like(ActivityEntity::getTitle, req.getQ())
                        .or()
                        .like(ActivityEntity::getDescription, req.getQ()))
                .eq(req.getType() != null, ActivityEntity::getActivityType, req.getType())
                .in(activityTypes(req).size() > 0, ActivityEntity::getActivityType, activityTypes(req))
                .eq(req.getCity() != null && !req.getCity().isBlank(), ActivityEntity::getCity, req.getCity())
                .eq(req.getFeeType() != null && !req.getFeeType().isBlank(), ActivityEntity::getFeeType, req.getFeeType())
                .ge(req.effectiveStartFrom() != null, ActivityEntity::getStartTime, req.effectiveStartFrom())
                .le(req.effectiveStartTo() != null, ActivityEntity::getStartTime, req.effectiveStartTo())
                .orderByAsc(ActivityEntity::getStartTime)).getRecords();
    }

    @Override
    public List<ActivityEntity> nearby(ActivityQueryReq req) {
        if (req.getLat() != null && req.getLng() != null) {
            return activityMapper.searchNearby(req.getLat(), req.getLng(),
                    req.effectiveRadiusMeters(),
                    req.normalizedLimit());
        }
        return latest(req);
    }

    private List<String> activityTypes(ActivityQueryReq req) {
        if (req.getActivityTypes() == null || req.getActivityTypes().isBlank()) {
            return List.of();
        }
        return Arrays.stream(req.getActivityTypes().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
