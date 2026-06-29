package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("follows")
public class FollowEntity {
    @TableId
    private String followerId;
    private String followedId;
    private LocalDateTime createdAt;
}
