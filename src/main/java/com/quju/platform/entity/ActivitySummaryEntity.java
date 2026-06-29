package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activity_summaries")
public class ActivitySummaryEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String activityId;
    private String content;
    private LocalDateTime createdAt;
}
