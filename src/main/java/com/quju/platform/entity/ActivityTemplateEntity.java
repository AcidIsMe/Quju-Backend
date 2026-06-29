package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "activity_templates", autoResultMap = true)
public class ActivityTemplateEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String name;
    private String category;
    private String description;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String activityType;
    private Integer presetDurationMinutes;
    private Integer presetMaxParticipants;
    @TableField("is_system")
    private Boolean system;
    private String creatorId;
    private LocalDateTime createdAt;
}
