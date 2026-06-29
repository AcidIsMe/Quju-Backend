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
@TableName(value = "notifications", autoResultMap = true)
public class NotificationEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String type;
    private String title;
    private String content;
    @TableField("is_read")
    private Boolean read;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;
    private LocalDateTime createdAt;
}
