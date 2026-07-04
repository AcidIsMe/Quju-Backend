package com.quju.platform.service;

import java.util.List;
import java.util.Map;

public interface MerchantService {
    void approve(String merchantId, String adminId);
    void reject(String merchantId, String reason, String adminId);

    /**
     * 公开商家列表（仅返回已审核通过的商家）
     * @param keyword  搜索关键词（模糊匹配商家名称/昵称）
     * @param domain   活动领域筛选（模糊匹配 activity_domains JSON 字段）
     * @param page     页码（从1开始）
     * @param size     每页条数
     * @return {data: List, pagination: {total, page, size}}
     */
    Map<String, Object> listApproved(String keyword, String domain, int page, int size);
}
