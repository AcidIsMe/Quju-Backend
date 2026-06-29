package com.quju.platform.component.ai;

import com.quju.platform.dto.activity.AiFormSchemaResp;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmClient {

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
