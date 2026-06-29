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
@TableName(value = "registrations", autoResultMap = true)
public class RegistrationEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String activityId;
    private String userId;
    private String status;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> formData;
    private LocalDateTime createdAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime checkedInAt;
}
