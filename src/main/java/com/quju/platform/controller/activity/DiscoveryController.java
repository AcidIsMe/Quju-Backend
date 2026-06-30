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

    @GetMapping({"/nearby", "/map"})
    public ApiResponse<?> nearby(@ModelAttribute ActivityQueryReq req) {
        CursorPage<ActivityEntity> page = discoveryService.nearby(req);
        return ApiResponse.page(page.getItems(), paginationMap(page));
    }

    private Map<String, Object> paginationMap(CursorPage<?> page) {
        Map<String, Object> map = new HashMap<>();
        map.put("cursor", page.getNextCursor());
        map.put("has_more", page.getHasMore());
        map.put("limit", page.getLimit());
        return map;
    }
}
