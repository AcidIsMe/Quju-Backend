package com.quju.common;

import jakarta.servlet.http.HttpServletRequest;

public class AuthUtil {

    private AuthUtil() {}

    /**
     * 从 request 属性中获取当前用户，如果未认证则抛出异常
     */
    public static com.quju.user.entity.User getCurrentUser(HttpServletRequest request) {
        com.quju.user.entity.User user = (com.quju.user.entity.User) request.getAttribute("currentUser");
        if (user == null) {
            throw new com.quju.auth.service.BizException(40101, "请先登录");
        }
        return user;
    }
}
