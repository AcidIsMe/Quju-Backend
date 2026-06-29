package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.activity.ActivityQueryReq;
import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DiscoveryServiceImpl implements DiscoveryService {

    private final ActivityMapper activityMapper;

    @Override
    public CursorPage<ActivityEntity> latest(ActivityQueryReq req) {
        LambdaQueryWrapper<ActivityEntity> wrapper = Wrappers.<ActivityEntity>lambdaQuery()
                .eq(req.getStatus() != null, ActivityEntity::getStatus, req.getStatus())
                .eq(req.getStatus() == null, ActivityEntity::getStatus, "published")
                .eq(req.getCity() != null && !req.getCity().isBlank(), ActivityEntity::getCity, req.getCity())
                .eq(req.getFeeType() != null && !req.getFeeType().isBlank(), ActivityEntity::getFeeType, req.getFeeType());
        applyCursorDesc(wrapper, req.getCursor());
        wrapper.orderByDesc(ActivityEntity::getCreatedAt).orderByDesc(ActivityEntity::getId);
        List<ActivityEntity> items = activityMapper.selectList(wrapper.last("LIMIT " + (req.normalizedLimit() + 1)));
        return CursorPage.of(items, req.normalizedLimit(), e -> {
            LocalDateTime t = e.getCreatedAt();
            return (t == null ? LocalDateTime.now() : t) + "|" + e.getId();
        });
    }

    @Override
    public CursorPage<ActivityEntity> recommended(ActivityQueryReq req) {
        return latest(req);
    }

    @Override
    public CursorPage<ActivityEntity> search(ActivityQueryReq req) {
        LambdaQueryWrapper<ActivityEntity> wrapper = Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getStatus, "published")
                .and(req.getQ() != null && !req.getQ().isBlank(), w -> w
                        .like(ActivityEntity::getTitle, req.getQ())
                        .or()
                        .like(ActivityEntity::getDescription, req.getQ()))
                .eq(req.getType() != null, ActivityEntity::getActivityType, req.getType())
                .in(activityTypes(req).size() > 0, ActivityEntity::getActivityType, activityTypes(req))
                .eq(req.getCity() != null && !req.getCity().isBlank(), ActivityEntity::getCity, req.getCity())
                .eq(req.getFeeType() != null && !req.getFeeType().isBlank(), ActivityEntity::getFeeType, req.getFeeType())
                .ge(req.effectiveStartFrom() != null, ActivityEntity::getStartTime, req.effectiveStartFrom())
                .le(req.effectiveStartTo() != null, ActivityEntity::getStartTime, req.effectiveStartTo());
        applyCursorAsc(wrapper, req.getCursor());
        wrapper.orderByAsc(ActivityEntity::getStartTime).orderByAsc(ActivityEntity::getId);
        List<ActivityEntity> items = activityMapper.selectList(wrapper.last("LIMIT " + (req.normalizedLimit() + 1)));
        return CursorPage.of(items, req.normalizedLimit(), e -> {
            LocalDateTime t = e.getStartTime();
            return (t == null ? LocalDateTime.now() : t) + "|" + e.getId();
        });
    }

    @Override
    public CursorPage<ActivityEntity> nearby(ActivityQueryReq req) {
        if (req.getLat() != null && req.getLng() != null) {
            List<ActivityEntity> items = activityMapper.searchNearby(req.getLat(), req.getLng(),
                    req.effectiveRadiusMeters(),
                    req.normalizedLimit() + 1);
            return CursorPage.of(items, req.normalizedLimit(), e -> {
                LocalDateTime t = e.getCreatedAt();
                return (t == null ? LocalDateTime.now() : t) + "|" + e.getId();
            });
        }
        return latest(req);
    }

    private void applyCursorDesc(LambdaQueryWrapper<ActivityEntity> wrapper, String cursor) {
        if (cursor == null || cursor.isBlank() || !cursor.contains("|")) return;
        String[] parts = cursor.split("\\|", 2);
        if (parts.length < 2) return;
        try {
            LocalDateTime time = LocalDateTime.parse(parts[0]);
            String id = parts[1];
            wrapper.and(w -> w
                    .lt(ActivityEntity::getCreatedAt, time)
                    .or(w2 -> w2
                            .eq(ActivityEntity::getCreatedAt, time)
                            .lt(ActivityEntity::getId, id)));
        } catch (Exception ignored) {
        }
    }

    private void applyCursorAsc(LambdaQueryWrapper<ActivityEntity> wrapper, String cursor) {
        if (cursor == null || cursor.isBlank() || !cursor.contains("|")) return;
        String[] parts = cursor.split("\\|", 2);
        if (parts.length < 2) return;
        try {
            LocalDateTime time = LocalDateTime.parse(parts[0]);
            String id = parts[1];
            wrapper.and(w -> w
                    .gt(ActivityEntity::getStartTime, time)
                    .or(w2 -> w2
                            .eq(ActivityEntity::getStartTime, time)
                            .gt(ActivityEntity::getId, id)));
        } catch (Exception ignored) {
        }
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
