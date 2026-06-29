package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "friendships", autoResultMap = true)
public class FriendshipEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String friendId;
    private String status;
    private String actionUserId;
    private String remarkName;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> groupTags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
