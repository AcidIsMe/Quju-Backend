package com.quju.platform.service;

public interface MerchantService {
    void approve(String merchantId, String adminId);
    void reject(String merchantId, String reason, String adminId);
}
