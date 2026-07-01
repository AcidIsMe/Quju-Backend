package com.quju.platform.controller.ai;

import com.quju.platform.component.ai.CvClient;
import com.quju.platform.component.ai.LlmClient;
import com.quju.platform.dto.activity.AiFormSchemaResp;
import com.quju.platform.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final LlmClient llmClient;
    private final CvClient cvClient;

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
}
