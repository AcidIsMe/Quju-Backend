package com.quju.platform.controller.activity;

import com.quju.platform.dto.activity.ActivityQueryReq;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/discover")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @GetMapping("/latest")
    public ApiResponse<List<ActivityEntity>> latest(@ModelAttribute ActivityQueryReq req) {
        return ApiResponse.ok(discoveryService.latest(req));
    }

    @GetMapping("/recommended")
    public ApiResponse<List<ActivityEntity>> recommended(@ModelAttribute ActivityQueryReq req) {
        return ApiResponse.ok(discoveryService.recommended(req));
    }

    @GetMapping({"/search", "/filter"})
    public ApiResponse<List<ActivityEntity>> search(@ModelAttribute ActivityQueryReq req) {
        return ApiResponse.ok(discoveryService.search(req));
    }

    @GetMapping({"/nearby", "/map"})
    public ApiResponse<List<ActivityEntity>> nearby(@ModelAttribute ActivityQueryReq req) {
        return ApiResponse.ok(discoveryService.nearby(req));
    }
}
