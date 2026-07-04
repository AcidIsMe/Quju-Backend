package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_announcements")
public class TeamAnnouncementEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String teamId;
    private String content;
    private Boolean pinned;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
