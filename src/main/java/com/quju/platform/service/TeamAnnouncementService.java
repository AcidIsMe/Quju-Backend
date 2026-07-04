package com.quju.platform.service;

import java.util.List;
import java.util.Map;

public interface TeamAnnouncementService {
    /**
     * 创建小队公告（仅队长/管理员可操作）
     */
    void create(String teamId, String operatorId, String content);

    /**
     * 查询小队公告列表（按 pinned DESC, created_at DESC 排序）
     */
    List<Map<String, Object>> list(String teamId);

    /**
     * 删除小队公告（仅队长/管理员可操作）
     */
    void delete(String teamId, String operatorId, String announcementId);

    /**
     * 切换公告置顶状态（仅队长/管理员可操作）
     */
    void togglePin(String teamId, String operatorId, String announcementId);
}
