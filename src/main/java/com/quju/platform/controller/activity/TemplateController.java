package com.quju.platform.controller.activity;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.service.TemplateService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String category) {
        return ApiResponse.ok(templateService.list(category));
    }

    @PostMapping("/{id}/use")
    public ApiResponse<ActivityEntity> use(@PathVariable String id) {
        return ApiResponse.ok(templateService.useTemplate(id, SecurityUtil.requireCurrentUserId()));
    }
}
