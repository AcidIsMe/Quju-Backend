package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_photos")
public class TeamPhotoEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String albumId;
    private String teamId;
    private String imageUrl;
    private String thumbnailUrl;
    private String description;
    private String uploadedBy;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
