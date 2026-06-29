package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_join_requests")
public class TeamJoinRequestEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String teamId;
    private String userId;
    private String status;
    private String reviewedBy;
    private LocalDateTime createdAt;
}
