package com.quju.platform.dto.im;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ImMessageDto {

    private String id;

    @NotBlank
    @JsonProperty("entity_type")
    private String entityType;

    @NotBlank
    @JsonProperty("entity_id")
    private String entityId;

    @JsonProperty("sender_id")
    private String senderId;

    private String type = "text";

    private String content;

    @JsonProperty("mention_all")
    private Boolean mentionAll = false;

    @JsonProperty("mention_user_ids")
    private List<String> mentionUserIds;

    private Map<String, Object> metadata;
}
