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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow LLM 客户端
 * 调用 DeepSeek-V3.2 进行：
 * - 内容安全深度审核 (deepReview)
 * - AI 活动策划生成 (generateActivity)
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

    public boolean isConfigured() {
        var aiConfig = qujuProperties.getAi().getSiliconflow();
        String apiKey = aiConfig.getApiKey();
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * 调用 DeepSeek-V3.2 进行深度审核
     *
     * @return "pass" | "violation" | "uncertain"
     */
    public String deepReview(String title, String description, List<String> tags) {
        var aiConfig = qujuProperties.getAi().getSiliconflow();
        String apiKey = aiConfig.getApiKey();
        if (!isConfigured()) {
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
     * 调用 DeepSeek-V3.2 根据主题生成活动信息（AI 活动策划 US09）
     * <p>
     * 异常时降级返回默认活动信息，确保前端可正常使用。
     *
     * @param topic 活动主题（可为 null 或空白，此时返回默认信息）
     * @return AI 生成的活动表单建议，不会返回 null
     */
    public AiFormSchemaResp generateActivity(String topic) {
        var aiConfig = qujuProperties.getAi().getSiliconflow();
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SiliconFlow API Key 未配置，返回默认活动信息");
            return buildDefaultActivity(topic);
        }

        String prompt = buildGeneratePrompt(topic);

        Map<String, Object> requestBody = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个活动策划助手。请严格按照要求输出JSON。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7,
                "max_tokens", 512,
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

            return parseGenerateResult(response.getBody(), topic);
        } catch (Exception e) {
            log.error("LLM 活动策划调用失败，降级为默认信息", e);
            return buildDefaultActivity(topic);
        }
    }

    /**
     * 构建 AI 活动策划的 prompt
     */
    private String buildGeneratePrompt(String topic) {
        String safeTopic = (topic != null && !topic.isBlank()) ? topic : "趣味社交活动";
        return String.format("""
                请根据以下主题，生成一个完整的活动方案。

                主题：%s

                请严格按照以下 JSON 格式回复（不要包含其他内容）：
                {
                  "title": "活动标题",
                  "description": "活动详细描述，包含活动内容、流程安排等，50-200字",
                  "tags": ["标签1", "标签2", "标签3"],
                  "activity_type": "活动类型",
                  "suggested_duration_minutes": 120,
                  "suggested_max_participants": 20
                }

                要求：
                1. 标题简洁有吸引力，6-20字
                2. 描述充实具体，包含活动内容及大致流程，50-200字
                3. 标签2-4个，精准反映活动特征
                4. 活动类型从以下列表中选择最匹配的：运动健身、桌游娱乐、户外旅行、学习交流、聚餐聚会、兴趣活动、公益活动、其他
                5. 时长（60-480分钟）和人数（5-100人）根据活动类型合理设定
                """, safeTopic);
    }

    /**
     * 解析 AI 活动策划返回结果
     */
    private AiFormSchemaResp parseGenerateResult(JsonNode body, String topic) {
        if (body == null) return buildDefaultActivity(topic);

        try {
            String content = body.get("choices").get(0).get("message").get("content").asText();
            JsonNode resultJson = objectMapper.readTree(content);

            String title = jsonText(resultJson, "title");
            String description = jsonText(resultJson, "description");
            String activityType = jsonText(resultJson, "activity_type");
            Integer duration = resultJson.has("suggested_duration_minutes") && !resultJson.get("suggested_duration_minutes").isNull()
                    ? resultJson.get("suggested_duration_minutes").asInt() : 120;
            Integer maxParticipants = resultJson.has("suggested_max_participants") && !resultJson.get("suggested_max_participants").isNull()
                    ? resultJson.get("suggested_max_participants").asInt() : 20;

            List<String> tags = List.of();
            if (resultJson.has("tags") && resultJson.get("tags").isArray()) {
                List<String> tagList = new ArrayList<>();
                for (JsonNode tag : resultJson.get("tags")) {
                    String t = tag.asText();
                    if (t != null && !t.isBlank()) tagList.add(t);
                }
                tags = tagList;
            }

            // 验证必填字段
            if (title == null || title.isBlank()) {
                title = (topic != null && !topic.isBlank()) ? topic : "新的趣聚活动";
            }
            if (description == null || description.isBlank()) {
                description = (topic != null && !topic.isBlank())
                        ? "围绕「" + topic + "」主题组织的一场精彩活动，欢迎感兴趣的朋友报名参加。"
                        : "一场精彩的趣聚活动，欢迎感兴趣的朋友报名参加。";
            }
            String finalActivityType = (activityType != null && !activityType.isBlank()) ? activityType : "兴趣活动";

            log.info("AI 活动策划生成成功: title={}, type={}, duration={}, max={}",
                    title, finalActivityType, duration, maxParticipants);
            return AiFormSchemaResp.builder()
                    .title(title)
                    .description(description)
                    .tags(tags)
                    .activityType(finalActivityType)
                    .suggestedDurationMinutes(duration)
                    .suggestedMaxParticipants(maxParticipants)
                    .build();
        } catch (Exception e) {
            log.warn("解析 LLM 活动策划结果失败", e);
            return buildDefaultActivity(topic);
        }
    }

    /**
     * 安全获取 JSON 节点文本，null/空/缺失均返回 null
     */
    private String jsonText(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            String text = node.get(field).asText();
            return (text == null || text.isBlank()) ? null : text;
        }
        return null;
    }

    /**
     * 构建默认活动信息（LLM 不可用时的降级方案）
     */
    private AiFormSchemaResp buildDefaultActivity(String topic) {
        String title = (topic != null && !topic.isBlank()) ? topic : "新的趣聚活动";
        String description = (topic != null && !topic.isBlank())
                ? "围绕「" + topic + "」主题组织的一场精彩活动，欢迎感兴趣的朋友报名参加。"
                : "一场精彩的趣聚活动，欢迎感兴趣的朋友报名参加。";
        return AiFormSchemaResp.builder()
                .title(title)
                .description(description)
                .tags(List.of("兴趣", "同城", "周末"))
                .activityType("兴趣活动")
                .suggestedDurationMinutes(120)
                .suggestedMaxParticipants(20)
                .build();
    }
}
