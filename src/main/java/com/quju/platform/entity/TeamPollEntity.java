package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("team_polls")
public class TeamPollEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String teamId;
    private String title;
    private String createdBy;
    private Boolean closed;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
}
