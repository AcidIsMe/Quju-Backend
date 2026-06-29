package com.quju.platform.util;

import com.quju.platform.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static String requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(40100, "请先登录");
        }
        return String.valueOf(authentication.getPrincipal());
    }

    /**
     * 获取当前用户 ID，若未认证返回 null（用于允许匿名访问的接口）
     */
    public static String getCurrentUserIdOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return String.valueOf(authentication.getPrincipal());
    }

    public static void requireAdminRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(40100, "请先登录");
        }
        String userId = String.valueOf(authentication.getPrincipal());
        // role is stored as a custom attribute in the authentication's details or authorities
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_admin".equals(a.getAuthority()) || "admin".equals(a.getAuthority()));
        if (!isAdmin) {
            throw new BusinessException(40300, "无权访问，需要管理员权限");
        }
    }
}
