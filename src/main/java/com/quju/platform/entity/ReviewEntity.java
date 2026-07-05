package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("reviews")
public class ReviewEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String activityId;
    private String userId;
    private String content;
    private Integer rating;
    private LocalDateTime createdAt;
}
