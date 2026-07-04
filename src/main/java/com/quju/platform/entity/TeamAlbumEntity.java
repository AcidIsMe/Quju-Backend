package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_albums")
public class TeamAlbumEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String teamId;
    private String name;
    private String description;
    private String coverImageUrl;
    private Integer photoCount;
    private String createdBy;
    private LocalDateTime createdAt;
}
