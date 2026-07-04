package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("poll_votes")
public class PollVoteEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String pollId;
    private String optionId;
    private String userId;
    private LocalDateTime createdAt;
}
