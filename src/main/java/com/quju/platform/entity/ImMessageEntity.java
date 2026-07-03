package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "im_messages", autoResultMap = true)
public class ImMessageEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String entityType;
    private String entityId;
    private String senderId;
    private String type;
    private String content;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
    private Boolean recalled;
    private LocalDateTime createdAt;
    private LocalDateTime recalledAt;
    private LocalDateTime readAt;
}
