package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("login_attempts")
public class LoginAttemptEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String email;
    private String ipAddress;
    private Boolean success;
    private LocalDateTime createdAt;
}
