package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("waitlist")
public class WaitlistEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String activityId;
    private String userId;
    private Integer position;
    private String status;
    private LocalDateTime notifiedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
