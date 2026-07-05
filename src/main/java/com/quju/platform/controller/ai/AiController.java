package com.quju.platform.controller.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.component.ai.CvClient;
import com.quju.platform.component.ai.LlmClient;
import com.quju.platform.dto.activity.AiFormSchemaResp;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.ReviewEntity;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final LlmClient llmClient;
    private final CvClient cvClient;
    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;
    private final ReviewMapper reviewMapper;

    @PostMapping("/generate-activity")
    public ApiResponse<AiFormSchemaResp> generate(@RequestBody Map<String, String> body) {
        String topic = body.get("topic");
        if (topic == null || topic.isBlank()) {
            return ApiResponse.fail(40010, "活动主题不能为空，请输入一个主题词或短语");
        }
        return ApiResponse.ok(llmClient.generateActivity(topic));
    }

    @PostMapping("/classify-images")
    public ApiResponse<List<Map<String, Object>>> classify(@RequestParam("images") List<MultipartFile> images) {
        List<String> categories = cvClient.classify(images.size());
        return ApiResponse.ok(IntStream.range(0, images.size())
                .mapToObj(i -> Map.<String, Object>of("image_index", i, "category", categories.get(i)))
                .toList());
    }

    /**
     * AI 一键生成活动总结
     * 根据活动的描述、签到人数、用户评价、现场照片等信息，由 AI 生成活动总结文本
     * 可选传入 image_urls 让视觉模型先分析照片内容再融入总结
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/generate-summary")
    public ApiResponse<Map<String, Object>> generateSummary(@RequestBody Map<String, Object> body) {
        String activityId = (String) body.get("activity_id");
        if (activityId == null || activityId.isBlank()) {
            return ApiResponse.fail(40010, "活动ID不能为空");
        }

        // 查询活动信息
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            return ApiResponse.fail(40401, "活动不存在");
        }

        // 统计签到人数
        long checkInCount = registrationMapper.selectCount(
                Wrappers.<RegistrationEntity>lambdaQuery()
                        .eq(RegistrationEntity::getActivityId, activityId)
                        .eq(RegistrationEntity::getStatus, "checked_in"));

        // 统计总报名人数
        long totalRegistrations = registrationMapper.selectCount(
                Wrappers.<RegistrationEntity>lambdaQuery()
                        .eq(RegistrationEntity::getActivityId, activityId));

        // 获取用户评价（取最近10条）
        List<ReviewEntity> reviews = reviewMapper.selectList(
                Wrappers.<ReviewEntity>lambdaQuery()
                        .eq(ReviewEntity::getActivityId, activityId)
                        .orderByDesc(ReviewEntity::getCreatedAt)
                        .last("LIMIT 10"));

        // 构建评价文本
        String reviewsText;
        if (reviews.isEmpty()) {
            reviewsText = "暂无用户评价";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < reviews.size(); i++) {
                ReviewEntity review = reviews.get(i);
                sb.append((i + 1)).append(". ");
                if (review.getRating() != null) {
                    sb.append("评分").append(review.getRating()).append("/5，");
                }
                sb.append(review.getContent()).append("\n");
            }
            reviewsText = sb.toString();
        }

        // 视觉模型分析图片（如果传入了图片URL列表）
        String imageAnalysis = null;
        Object imageUrlsObj = body.get("image_urls");
        if (imageUrlsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> imageUrls = ((List<Object>) imageUrlsObj).stream()
                    .filter(String.class::isInstance)
                    .map(o -> (String) o)
                    .toList();
            if (!imageUrls.isEmpty()) {
                imageAnalysis = llmClient.analyzeImages(imageUrls);
            }
        }

        // 调用 LLM 生成总结（融入图片分析结果）
        String summary = llmClient.generateSummary(
                activity.getDescription(),
                (int) checkInCount,
                (int) totalRegistrations,
                reviewsText,
                imageAnalysis
        );

        if (summary == null) {
            return ApiResponse.fail(50001, "AI 总结生成失败：LLM 服务暂不可用，请确认 API Key 配置有效或稍后重试");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        result.put("check_in_count", checkInCount);
        result.put("total_registrations", totalRegistrations);
        result.put("image_analyzed", imageAnalysis != null);
        return ApiResponse.ok(result);
    }
}
