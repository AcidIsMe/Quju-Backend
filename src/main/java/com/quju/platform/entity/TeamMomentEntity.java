package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_moments")
public class TeamMomentEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String teamId;
    private String userId;
    private String content;
    private String imageUrl;
    private Boolean featured;
    private String featuredBy;
    private LocalDateTime featuredAt;
    private LocalDateTime createdAt;
}
