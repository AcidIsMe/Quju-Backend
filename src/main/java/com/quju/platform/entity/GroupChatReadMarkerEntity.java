package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群聊已读标记 - 记录每位用户在群组中的最后阅读时间
 */
@Data
@TableName("group_chat_read_markers")
public class GroupChatReadMarkerEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String groupId;
    private String userId;
    private LocalDateTime lastReadAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
