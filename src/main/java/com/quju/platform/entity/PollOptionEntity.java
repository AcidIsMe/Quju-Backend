package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("poll_options")
public class PollOptionEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String pollId;
    private String optionText;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
