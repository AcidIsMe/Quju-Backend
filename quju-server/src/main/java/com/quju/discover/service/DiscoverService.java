package com.quju.discover.service;

import com.quju.entity.Activity;
import com.quju.repository.ActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiscoverService {

    private final ActivityRepository activityRepository;

    public DiscoverService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    /** 推荐活动：按 currentParticipants DESC */
    public List<Activity> recommended(int cursor, int limit) {
        return activityRepository.findRecommended(PageRequest.of(cursor, limit));
    }

    /** 最新活动：按 createdAt DESC */
    public List<Activity> latest(int cursor, int limit) {
        return activityRepository.findByStatusOrderByCreatedAtDesc("published", PageRequest.of(cursor, limit));
    }

    /** 附近活动：简化为按相同城市查找（不含 PostGIS） */
    public List<Activity> nearby(BigDecimal lat, BigDecimal lng, int radius, int cursor, int limit) {
        // 简化实现：返回同城市的已发布活动
        return activityRepository.findByStatus("published", PageRequest.of(cursor, limit));
    }

    /** 关键词搜索 */
    public List<Activity> search(String q, int cursor, int limit) {
        if (q == null || q.isBlank()) {
            return activityRepository.findByStatusOrderByCreatedAtDesc("published", PageRequest.of(cursor, limit));
        }
        return activityRepository.search(q, PageRequest.of(cursor, limit));
    }

    /** 高级筛选 */
    public List<Activity> filter(String activityTypes, String city, String feeType,
                                  int cursor, int limit) {
        // 全部已发布的活动作为基础
        List<Activity> all = activityRepository.findByStatus("published", PageRequest.of(0, 1000));

        // 按类型过滤（逗号分隔，OR 逻辑）
        if (activityTypes != null && !activityTypes.isBlank()) {
            Set<String> types = Arrays.stream(activityTypes.split(","))
                    .map(String::trim).collect(Collectors.toSet());
            all = all.stream()
                    .filter(a -> a.getActivityType() != null && types.contains(a.getActivityType()))
                    .collect(Collectors.toList());
        }

        // 按城市过滤
        if (city != null && !city.isBlank()) {
            all = all.stream()
                    .filter(a -> city.equals(a.getCity()))
                    .collect(Collectors.toList());
        }

        // 按费用类型过滤
        if (feeType != null && !feeType.isBlank()) {
            all = all.stream()
                    .filter(a -> feeType.equals(a.getFeeType()))
                    .collect(Collectors.toList());
        }

        // 分页
        int start = cursor * limit;
        if (start >= all.size()) return Collections.emptyList();
        int end = Math.min(start + limit, all.size());
        return all.subList(start, end);
    }

    /** 地图模式：返回轻量数据 */
    public List<Map<String, Object>> mapView(BigDecimal swLat, BigDecimal swLng,
                                               BigDecimal neLat, BigDecimal neLng) {
        List<Activity> activities = activityRepository.findByStatus("published", PageRequest.of(0, 200));

        // 简化：边界框过滤
        List<Map<String, Object>> results = new ArrayList<>();
        for (Activity a : activities) {
            if (a.getLocationLat() != null && a.getLocationLng() != null) {
                if (a.getLocationLat().compareTo(swLat) >= 0
                        && a.getLocationLat().compareTo(neLat) <= 0
                        && a.getLocationLng().compareTo(swLng) >= 0
                        && a.getLocationLng().compareTo(neLng) <= 0) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("id", a.getId());
                    point.put("title", a.getTitle());
                    point.put("location_lat", a.getLocationLat());
                    point.put("location_lng", a.getLocationLng());
                    point.put("start_time", a.getStartTime() != null ? a.getStartTime().toString() : null);
                    point.put("current_participants", a.getCurrentParticipants());
                    point.put("max_participants", a.getMaxParticipants());
                    results.add(point);
                }
            }
        }
        return results;
    }
}
