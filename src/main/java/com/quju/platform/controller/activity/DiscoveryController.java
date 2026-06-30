package com.quju.platform.controller.activity;

import com.quju.platform.dto.activity.ActivityQueryReq;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.common.CursorPage;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/discover")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @GetMapping("/latest")
    public ApiResponse<?> latest(@ModelAttribute ActivityQueryReq req) {
        CursorPage<ActivityEntity> page = discoveryService.latest(req);
        return ApiResponse.page(page.getItems(), paginationMap(page));
    }

    @GetMapping("/recommended")
    public ApiResponse<?> recommended(@ModelAttribute ActivityQueryReq req) {
        CursorPage<ActivityEntity> page = discoveryService.recommended(req);
        return ApiResponse.page(page.getItems(), paginationMap(page));
    }

    @GetMapping({"/search", "/filter"})
    public ApiResponse<?> search(@ModelAttribute ActivityQueryReq req) {
        CursorPage<ActivityEntity> page = discoveryService.search(req);
        return ApiResponse.page(page.getItems(), paginationMap(page));
    }

    @GetMapping({"/nearby"})
    public ApiResponse<?> nearby(@ModelAttribute ActivityQueryReq req) {
        CursorPage<ActivityEntity> page = discoveryService.nearby(req);
        return ApiResponse.page(page.getItems(), paginationMap(page));
    }

    @GetMapping("/map")
    public ApiResponse<?> map(@ModelAttribute ActivityQueryReq req) {
        // 地图模式使用边界框查询（US17 AC3）
        if (req.getSwLat() != null && req.getSwLng() != null
                && req.getNeLat() != null && req.getNeLng() != null) {
            CursorPage<ActivityEntity> page = discoveryService.mapBox(req);
            return ApiResponse.page(page.getItems(), paginationMap(page));
        }
        // 兜底：若无边界框参数，回退到半径查询
        CursorPage<ActivityEntity> page = discoveryService.nearby(req);
        return ApiResponse.page(page.getItems(), paginationMap(page));
    }

    private Map<String, Object> paginationMap(CursorPage<?> page) {
        Map<String, Object> map = new HashMap<>();
        map.put("next_cursor", page.getNextCursor());
        map.put("has_more", page.getHasMore());
        map.put("limit", page.getLimit());
        return map;
    }
}
