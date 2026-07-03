package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.activity.ActivityQueryReq;
import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.exception.BusinessException;
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
        // 推荐策略：热门优先（报名人数多、近期发布），仅展示已发布且未结束的活动
        LambdaQueryWrapper<ActivityEntity> wrapper = Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getStatus, "published")
                .ge(ActivityEntity::getEndTime, LocalDateTime.now())
                .eq(req.getCity() != null && !req.getCity().isBlank(), ActivityEntity::getCity, req.getCity())
                .eq(req.getFeeType() != null && !req.getFeeType().isBlank(), ActivityEntity::getFeeType, req.getFeeType());
        applyCursorDesc(wrapper, req.getCursor());
        wrapper.orderByDesc(ActivityEntity::getCurrentParticipants)
                .orderByDesc(ActivityEntity::getCreatedAt)
                .orderByDesc(ActivityEntity::getId);
        List<ActivityEntity> items = activityMapper.selectList(wrapper.last("LIMIT " + (req.normalizedLimit() + 1)));
        return CursorPage.of(items, req.normalizedLimit(), e -> {
            LocalDateTime t = e.getCreatedAt();
            return (t == null ? LocalDateTime.now() : t) + "|" + e.getId();
        });
    }

    @Override
    public CursorPage<ActivityEntity> search(ActivityQueryReq req) {
        if (req.getQ() == null || req.getQ().isBlank()) {
            // 无关键词时退化为 latest（US15 AC3）
            return latest(req);
        }
        // 关键词搜索：按相关度排序（标题 > 标签 > 简介 > 发布时间）（US15 AC1）
        int fetchLimit = req.normalizedLimit() + 1;
        List<ActivityEntity> items = activityMapper.searchWithRelevance(
                req.getQ(),
                req.getCity(),
                req.getFeeType(),
                req.getActivityTypes(),
                req.effectiveStartFrom() != null ? req.effectiveStartFrom().toString() : null,
                req.effectiveStartTo() != null ? req.effectiveStartTo().toString() : null,
                fetchLimit);
        // 搜索无结果时，返回推荐作为兜底（US15 AC2）
        if (items.isEmpty()) {
            return recommended(req);
        }
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
        // AC5: 未提供位置信息 → 提示需要位置权限
        throw new BusinessException(40010, "需要位置权限");
    }

    @Override
    public CursorPage<ActivityEntity> mapBox(ActivityQueryReq req) {
        if (req.getSwLat() == null || req.getSwLng() == null
                || req.getNeLat() == null || req.getNeLng() == null) {
            // AC5: 地图模式下未提供边界框 → 提示需要位置权限
            throw new BusinessException(40010, "需要位置权限");
        }
        int limit = req.normalizedLimit() + 1;
        List<ActivityEntity> items = activityMapper.searchMapBox(
                req.getSwLat(), req.getSwLng(),
                req.getNeLat(), req.getNeLng(),
                limit);
        return CursorPage.of(items, req.normalizedLimit(), e -> {
            LocalDateTime t = e.getStartTime();
            return (t == null ? LocalDateTime.now() : t) + "|" + e.getId();
        });
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
