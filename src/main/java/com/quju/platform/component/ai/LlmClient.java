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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * SiliconFlow LLM 客户端
 * 调用 DeepSeek-V3.2 进行：
 * - 内容安全深度审核 (deepReview)
 * - AI 活动策划生成 (generateActivity)
 * - AI 活动总结生成 (generateSummary)
 * 调用 Qwen/Qwen3-VL-8B-Instruct 进行：
 * - 活动照片视觉分析 (analyzeImages)
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

    private static final String VISION_MODEL = "Qwen/Qwen3-VL-8B-Instruct";

    /**
     * 调用 Qwen/Qwen3-VL-8B-Instruct 视觉模型分析活动照片
     * 支持远程 URL 和本地相对路径（自动转为 base64 data URI）
     *
     * @param imageUrls 照片 URL 列表（可为完整 http(s) 地址或 /uploads/ 相对路径）
     * @return 图片内容描述文本，异常时返回 null
     */
    public String analyzeImages(List<String> imageUrls) {
        var aiConfig = qujuProperties.getAi().getSiliconflow();
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SiliconFlow API Key 未配置，无法分析图片");
            return null;
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }

        String uploadDir = qujuProperties.getFiles().getUploadDir();
        Path uploadBasePath = Paths.get(uploadDir).toAbsolutePath();

        // 构建多模态消息内容
        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text",
                "请逐一描述以下活动照片的内容，包括：场景类型（合影/场地/活动过程/物资/成果）、画面中的人物活动、氛围感受。请用简洁的中文描述，每张照片1-2句话。"));

        for (String imageUrl : imageUrls) {
            String resolvedUrl = resolveImageForVision(imageUrl, uploadBasePath);
            if (resolvedUrl != null) {
                contentParts.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", resolvedUrl)
                ));
            }
        }

        if (contentParts.size() <= 1) {
            log.warn("没有可分析的图片");
            return null;
        }

        Map<String, Object> requestBody = Map.of(
                "model", VISION_MODEL,
                "messages", List.of(
                        Map.of("role", "user", "content", contentParts)
                ),
                "temperature", 0.3,
                "max_tokens", 600
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("Connection", "keep-alive");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    aiConfig.getBaseUrl() + "/chat/completions",
                    HttpMethod.POST,
                    entity,
                    JsonNode.class
            );

            String result = parsePlainTextResult(response.getBody());
            if (result != null) {
                log.info("视觉模型图片分析成功，{} 张图片 -> {} 字", imageUrls.size(), result.length());
            }
            return result;
        } catch (Exception e) {
            log.error("视觉模型图片分析失败", e);
            return null;
        }
    }

    /**
     * 解析图片地址供视觉模型使用：
     * - 远程 http(s) URL → 直接返回
     * - /uploads/ 本地相对路径 → 读取文件转 base64 data URI
     * - 其他 → null
     */
    private String resolveImageForVision(String imageUrl, Path uploadBasePath) {
        if (imageUrl == null || imageUrl.isBlank()) return null;

        // 已是远程 URL，直接使用
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return imageUrl;
        }

        // 本地相对路径，读取文件转 base64
        try {
            // 去掉开头的 /uploads/ 前缀，拼接到实际存储目录
            String relativePath = imageUrl;
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1); // 去掉开头的 /
            }
            // 如果路径以 uploads/ 开头，去掉它（因为 uploadBasePath 已经指向 uploads 目录）
            if (relativePath.startsWith("uploads/")) {
                relativePath = relativePath.substring("uploads/".length());
            }

            Path filePath = uploadBasePath.resolve(relativePath);
            if (!Files.exists(filePath)) {
                log.warn("图片文件不存在: {}", filePath);
                return null;
            }

            byte[] bytes = Files.readAllBytes(filePath);
            String base64 = Base64.getEncoder().encodeToString(bytes);

            // 根据文件扩展名确定 MIME 类型
            String fileName = filePath.getFileName().toString().toLowerCase();
            String mimeType = "image/jpeg";
            if (fileName.endsWith(".png")) {
                mimeType = "image/png";
            } else if (fileName.endsWith(".gif")) {
                mimeType = "image/gif";
            } else if (fileName.endsWith(".webp")) {
                mimeType = "image/webp";
            } else if (fileName.endsWith(".bmp")) {
                mimeType = "image/bmp";
            }

            return "data:" + mimeType + ";base64," + base64;
        } catch (IOException e) {
            log.warn("读取图片文件失败: {}", imageUrl, e);
            return null;
        }
    }

    /**
     * 调用 DeepSeek-V3.2 根据活动信息 + 图片分析生成活动总结
     *
     * @param activityDescription 活动描述
     * @param checkInCount        签到人数
     * @param totalRegistrations  总报名人数
     * @param reviewsText         用户评价汇总文本
     * @param imageAnalysis       图片视觉分析结果（可为 null）
     * @return AI 生成的活动总结文本，异常时返回 null
     */
    public String generateSummary(String activityDescription, int checkInCount, int totalRegistrations,
                                  String reviewsText, String imageAnalysis) {
        var aiConfig = qujuProperties.getAi().getSiliconflow();
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("SiliconFlow API Key 未配置，无法生成 AI 活动总结");
            return null;
        }

        String prompt = buildSummaryPrompt(activityDescription, checkInCount, totalRegistrations, reviewsText, imageAnalysis);

        Map<String, Object> requestBody = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一个活动总结撰写助手。请根据提供的活动信息和照片描述，撰写一份生动有趣的活动总结回顾。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7,
                "max_tokens", 500
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

            return parsePlainTextResult(response.getBody());
        } catch (Exception e) {
            log.error("LLM 活动总结生成失败", e);
            return null;
        }
    }

    /**
     * 兼容旧接口（无图片分析），委托到新方法
     */
    public String generateSummary(String activityDescription, int checkInCount, int totalRegistrations, String reviewsText) {
        return generateSummary(activityDescription, checkInCount, totalRegistrations, reviewsText, null);
    }

    /**
     * 构建 AI 活动总结的 prompt（含可选图片分析）
     */
    private String buildSummaryPrompt(String activityDescription, int checkInCount, int totalRegistrations,
                                      String reviewsText, String imageAnalysis) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下活动信息，撰写一份活动总结回顾。\n\n");
        sb.append("活动描述：").append(activityDescription).append("\n");
        sb.append("签到人数：").append(checkInCount).append(" 人（共 ").append(totalRegistrations).append(" 人报名）\n");
        sb.append("用户评价：\n").append(reviewsText).append("\n");

        if (imageAnalysis != null && !imageAnalysis.isBlank()) {
            sb.append("现场照片描述（由视觉AI分析）：\n").append(imageAnalysis).append("\n");
        }

        sb.append("""
                请撰写一份生动有趣、有温度的活动总结，要求：
                1. 开头简要回顾活动的亮点和整体氛围
                2. 中间总结活动的参与情况（签到率、参与度等）
                3. 结合用户评价中的反馈和照片中的场景，提炼活动的亮点和改进建议
                4. 结尾表达感谢和期待
                5. 严格控制在 150-250 字，不得超出
                6. 语气亲切自然，适合发布在社交平台

                请直接输出总结文本，不需要 JSON 格式，不需要标题。
                """);
        return sb.toString();
    }

    /**
     * 解析 LLM 返回的纯文本结果
     */
    private String parsePlainTextResult(JsonNode body) {
        if (body == null) return null;

        try {
            String content = body.get("choices").get(0).get("message").get("content").asText();
            if (content != null && !content.isBlank()) {
                log.info("LLM 生成成功，长度: {} 字", content.length());
                return content.trim();
            }
        } catch (Exception e) {
            log.warn("解析 LLM 结果失败", e);
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
