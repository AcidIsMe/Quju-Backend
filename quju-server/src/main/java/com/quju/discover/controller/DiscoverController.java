package com.quju.discover.controller;

import com.quju.common.ApiResponse;
import com.quju.discover.service.DiscoverService;
import com.quju.entity.Activity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/discover")
public class DiscoverController {

    private final DiscoverService discoverService;

    public DiscoverController(DiscoverService discoverService) {
        this.discoverService = discoverService;
    }

    /** 推荐活动 */
    @GetMapping("/recommended")
    public ApiResponse<List<Activity>> recommended(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(discoverService.recommended(cursor, limit));
    }

    /** 最新活动 */
    @GetMapping("/latest")
    public ApiResponse<List<Activity>> latest(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(discoverService.latest(cursor, limit));
    }

    /** 附近活动 */
    @GetMapping("/nearby")
    public ApiResponse<List<Activity>> nearby(
            @RequestParam BigDecimal lat,
            @RequestParam BigDecimal lng,
            @RequestParam(defaultValue = "5000") int radius,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(discoverService.nearby(lat, lng, radius, cursor, limit));
    }

    /** 关键词搜索 */
    @GetMapping("/search")
    public ApiResponse<List<Activity>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(discoverService.search(q, cursor, limit));
    }

    /** 高级筛选 */
    @GetMapping("/filter")
    public ApiResponse<List<Activity>> filter(
            @RequestParam(required = false) String activityTypes,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String feeType,
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(discoverService.filter(activityTypes, city, feeType, cursor, limit));
    }

    /** 地图模式 */
    @GetMapping("/map")
    public ApiResponse<List<Map<String, Object>>> mapView(
            @RequestParam(name = "sw_lat") BigDecimal swLat,
            @RequestParam(name = "sw_lng") BigDecimal swLng,
            @RequestParam(name = "ne_lat") BigDecimal neLat,
            @RequestParam(name = "ne_lng") BigDecimal neLng) {
        return ApiResponse.success(discoverService.mapView(swLat, swLng, neLat, neLng));
    }
}
