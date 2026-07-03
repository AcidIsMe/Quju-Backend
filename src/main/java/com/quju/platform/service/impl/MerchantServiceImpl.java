package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.MerchantProfileEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.MerchantProfileMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.MerchantService;
import com.quju.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantProfileMapper merchantProfileMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public void approve(String merchantId, String adminId) {
        MerchantProfileEntity profile = require(merchantId);
        // 重复审核拦截
        if (!"pending".equals(profile.getAuditStatus())) {
            throw new BusinessException(40018, "该商家申请已被处理，请勿重复审核");
        }
        profile.setAuditStatus("approved");
        profile.setAuditedBy(adminId);
        profile.setAuditedAt(LocalDateTime.now());
        merchantProfileMapper.updateById(profile);
        // 更新用户状态为已激活
        UserEntity user = userMapper.selectById(profile.getUserId());
        if (user != null) {
            user.setStatus("active");
            userMapper.updateById(user);
        }
        // 通知商家审核通过
        if (profile.getUserId() != null) {
            notificationService.notify(
                    profile.getUserId(),
                    "merchant_approved",
                    "商家审核通过",
                    "您的商家资料已通过审核，现在可以发布活动了。",
                    Map.of("merchant_id", merchantId)
            );
        }
    }

    @Override
    @Transactional
    public void reject(String merchantId, String reason, String adminId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(40019, "驳回原因必填");
        }
        MerchantProfileEntity profile = require(merchantId);
        // 重复审核拦截
        if (!"pending".equals(profile.getAuditStatus())) {
            throw new BusinessException(40018, "该商家申请已被处理，请勿重复审核");
        }
        profile.setAuditStatus("rejected");
        profile.setAuditReason(reason);
        profile.setAuditedBy(adminId);
        profile.setAuditedAt(LocalDateTime.now());
        merchantProfileMapper.updateById(profile);
        // 通知商家审核驳回
        if (profile.getUserId() != null) {
            notificationService.notify(
                    profile.getUserId(),
                    "merchant_rejected",
                    "商家审核驳回",
                    "您的商家资料未通过审核，原因：" + reason,
                    Map.of("merchant_id", merchantId, "reason", reason)
            );
        }
    }

    @Override
    public Map<String, Object> listApproved(String keyword, String domain, int page, int size) {
        int pageSize = Math.max(1, Math.min(size, 100));
        int offset = (Math.max(1, page) - 1) * pageSize;

        var wrapper = Wrappers.<MerchantProfileEntity>lambdaQuery()
                .eq(MerchantProfileEntity::getAuditStatus, "approved")
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MerchantProfileEntity::getMerchantName, keyword)
                        .or()
                        .like(MerchantProfileEntity::getMerchantNickname, keyword));
        if (domain != null && !domain.isBlank()) {
            wrapper.apply("activity_domains LIKE {0}", "%" + domain + "%");
        }

        long total = merchantProfileMapper.selectCount(wrapper);
        wrapper.orderByDesc(MerchantProfileEntity::getCreatedAt).orderByDesc(MerchantProfileEntity::getId);
        wrapper.last("LIMIT " + offset + "," + pageSize);

        List<Map<String, Object>> list = merchantProfileMapper.selectList(wrapper).stream()
                .map(this::toListItem)
                .toList();

        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("total", total);
        pagination.put("page", page);
        pagination.put("size", pageSize);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", list);
        result.put("pagination", pagination);
        return result;
    }

    private Map<String, Object> toListItem(MerchantProfileEntity merchant) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", merchant.getId());
        item.put("merchant_name", merchant.getMerchantName());
        item.put("merchant_nickname", merchant.getMerchantNickname());
        item.put("activity_domains", merchant.getActivityDomains());
        item.put("created_at", merchant.getCreatedAt());
        if (merchant.getUserId() != null) {
            UserEntity user = userMapper.selectById(merchant.getUserId());
            if (user != null) {
                item.put("avatar_url", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
            }
        }
        return item;
    }

    private MerchantProfileEntity require(String merchantId) {
        MerchantProfileEntity profile = merchantProfileMapper.selectById(merchantId);
        if (profile == null) {
            throw new BusinessException(40403, "商家资料不存在");
        }
        return profile;
    }
}
