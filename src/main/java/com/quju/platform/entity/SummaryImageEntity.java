package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("summary_images")
public class SummaryImageEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String summaryId;
    private String imageUrl;
    private String category;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
