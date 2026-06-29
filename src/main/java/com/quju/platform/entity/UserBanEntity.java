package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_bans")
public class UserBanEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String reason;
    private String bannedBy;
    private LocalDateTime bannedAt;
    private LocalDateTime expiresAt;
    @TableField("is_active")
    private Boolean active;
    private String revokedBy;
    private LocalDateTime revokedAt;
}
