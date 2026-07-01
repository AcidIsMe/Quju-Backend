package com.quju.platform.service;

import com.quju.platform.dto.auth.ChangePasswordReq;
import com.quju.platform.dto.auth.LoginReq;
import com.quju.platform.dto.auth.MerchantApplyReq;
import com.quju.platform.dto.auth.RegisterReq;

import java.util.Map;

public interface AuthService {
    Map<String, Object> registerPersonal(RegisterReq req);
    Map<String, Object> registerMerchant(MerchantApplyReq req);
    Map<String, Object> login(LoginReq req, String ipAddress);
    Map<String, Object> refresh(String refreshToken);
    void activate(String token);
    void resendActivation(String email);
    void logout(String refreshToken);
    void changePassword(String userId, ChangePasswordReq req);
}
