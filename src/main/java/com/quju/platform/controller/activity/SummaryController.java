package com.quju.platform.controller.activity;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.service.SummaryService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/activities/{activityId}/summary")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SummaryController {

    private final SummaryService summaryService;

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@PathVariable String activityId,
                                                   @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) body.getOrDefault("images", List.of());
        return ApiResponse.ok(summaryService.create(
                activityId,
                SecurityUtil.requireCurrentUserId(),
                (String) body.getOrDefault("content", ""),
                images
        ));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@PathVariable String activityId,
                                                   @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) body.getOrDefault("images", List.of());
        return ApiResponse.ok(summaryService.update(
                activityId,
                SecurityUtil.requireCurrentUserId(),
                (String) body.getOrDefault("content", ""),
                images
        ));
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> detail(@PathVariable String activityId) {
        return ApiResponse.ok(summaryService.detail(activityId));
    }

    @PostMapping("/classify-images")
    public ApiResponse<Map<String, Object>> classifyImages(@PathVariable String activityId,
                                                            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> imageUrls = (List<String>) body.get("image_urls");
        return ApiResponse.ok(summaryService.classifyImages(
                activityId,
                SecurityUtil.requireCurrentUserId(),
                imageUrls
        ));
    }

    @PutMapping("/images/{imageId}/category")
    public ApiResponse<Map<String, Object>> updateCategory(@PathVariable String activityId,
                                                            @PathVariable String imageId,
                                                            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(summaryService.updateImageCategory(
                activityId,
                SecurityUtil.requireCurrentUserId(),
                imageId,
                body.get("category")
        ));
    }
}
