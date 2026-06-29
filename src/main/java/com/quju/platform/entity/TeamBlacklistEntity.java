package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_blacklist")
public class TeamBlacklistEntity {
    @TableId
    private String teamId;
    private String userId;
    private LocalDateTime createdAt;
}
