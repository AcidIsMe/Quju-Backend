package com.quju.platform.service.impl;

import com.quju.platform.entity.MerchantProfileEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.MerchantProfileMapper;
import com.quju.platform.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MerchantProfileMapper merchantProfileMapper;

    @Override
    public void approve(String merchantId, String adminId) {
        MerchantProfileEntity profile = require(merchantId);
        profile.setAuditStatus("approved");
        profile.setAuditedBy(adminId);
        profile.setAuditedAt(LocalDateTime.now());
        merchantProfileMapper.updateById(profile);
    }

    @Override
    public void reject(String merchantId, String reason, String adminId) {
        MerchantProfileEntity profile = require(merchantId);
        profile.setAuditStatus("rejected");
        profile.setAuditReason(reason);
        profile.setAuditedBy(adminId);
        profile.setAuditedAt(LocalDateTime.now());
        merchantProfileMapper.updateById(profile);
    }

    private MerchantProfileEntity require(String merchantId) {
        MerchantProfileEntity profile = merchantProfileMapper.selectById(merchantId);
        if (profile == null) {
            throw new BusinessException(40403, "商家资料不存在");
        }
        return profile;
    }
}
