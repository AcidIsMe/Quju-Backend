package com.quju.platform.component.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.platform.config.QujuProperties;
import com.quju.platform.dto.activity.AiFormSchemaResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * SiliconFlow LLM 客户端
 * 调用 DeepSeek-V3.2 进行内容安全深度审核
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final QujuProperties qujuProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmClient(QujuProperties qujuProperties) {
        this.qujuProperties = qujuProperties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用 DeepSeek-V3.2 进行深度审核
     *
     * @return "pass" | "violation" | "uncertain"
     */
    public String deepReview(String title, String description, List<String> tags) {
        var aiConfig = qujuProperties.getAi().getSiliconflow();
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SiliconFlow API Key 未配置，跳过 LLM 深度审核");
            return "uncertain";
        }

        // 构建对话消息
        String prompt = buildReviewPrompt(title, description, tags);

        Map<String, Object> requestBody = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个中文内容安全审核助手。请严格按照要求输出JSON。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.1,
                "max_tokens", 256,
                "response_format", Map.of("type", "json_object")
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    aiConfig.getBaseUrl() + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    JsonNode.class
            );

            return parseReviewResult(response.getBody());
        } catch (Exception e) {
            log.error("LLM 深度审核调用失败", e);
            return "uncertain";
        }
    }

    private String buildReviewPrompt(String title, String description, List<String> tags) {
        String tagsStr = (tags == null || tags.isEmpty()) ? "无" : String.join("、", tags);
        return String.format("""
                请审核以下活动内容是否违规：

                标题：%s
                描述：%s
                标签：%s

                审核标准（仅关注以下违规类型）：
                1. 色情低俗：裸聊、约炮、色情描写等
                2. 赌博相关：赌场、赌球、博彩等
                3. 毒品违禁：毒品、违禁药品等
                4. 暴力恐怖：枪支、暴力袭击等
                5. 诈骗传销：虚假宣传、传销等
                6. 广告营销：推广联系方式、引导加微信等

                请严格按照以下 JSON 格式回复（不要包含其他内容）：
                {"result": "pass", "reason": "无违规内容"}
                或
                {"result": "violation", "reason": "包含赌博相关内容", "risk_type": "赌博"}
                或
                {"result": "uncertain", "reason": "疑似广告内容，需人工判断", "risk_type": "广告营销"}

                注意：只有明确违规才返回 violation，可疑但不确定返回 uncertain，没有违规返回 pass。
                """, title, description, tagsStr);
    }

    private String parseReviewResult(JsonNode body) {
        if (body == null) return "uncertain";

        try {
            // 提取 assistant 消息内容
            String content = body.get("choices").get(0).get("message").get("content").asText();
            // 解析 JSON
            JsonNode resultJson = objectMapper.readTree(content);
            String result = resultJson.get("result").asText("uncertain");

            if ("pass".equals(result) || "violation".equals(result) || "uncertain".equals(result)) {
                String reason = resultJson.has("reason") ? resultJson.get("reason").asText() : "";
                log.info("LLM 审核结果: {}, 原因: {}", result, reason);
                return result;
            }
        } catch (Exception e) {
            log.warn("解析 LLM 审核结果失败", e);
        }
        return "uncertain";
    }

    /**
     * 生成活动信息（可在未来接入 LLM 实现真正的 AI 辅助创建）
     */
    public AiFormSchemaResp generateActivity(String topic) {
        return AiFormSchemaResp.builder()
                .title(topic == null || topic.isBlank() ? "新的趣聚活动" : topic)
                .description("围绕主题进行的一场轻量活动，后续可接入真实 LLM 自动扩写。")
                .tags(List.of("兴趣", "同城", "周末"))
                .activityType("兴趣活动")
                .suggestedDurationMinutes(120)
                .suggestedMaxParticipants(20)
                .build();
    }
}
