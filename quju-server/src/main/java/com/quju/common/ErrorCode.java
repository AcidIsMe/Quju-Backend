package com.quju.common;

public final class ErrorCode {
    private ErrorCode() {}

    // 400xx 参数校验
    public static final int EMAIL_ALREADY_REGISTERED = 40001;
    public static final int PASSWORD_INVALID = 40002;
    public static final int NICKNAME_ALREADY_TAKEN = 40003;
    public static final int VALIDATION_FAILED = 40000;

    // 401xx 认证错误
    public static final int BAD_CREDENTIALS = 40101;
    public static final int ACCOUNT_NOT_ACTIVATED = 40102;
    public static final int ACCOUNT_BANNED = 40103;

    // 403xx 权限错误
    public static final int FORBIDDEN = 40300;
    public static final int CREDIT_SCORE_INSUFFICIENT = 40301;
    public static final int AGE_NOT_MET = 40304;
    public static final int NOT_ACTIVITY_CREATOR = 40305;

    // 404xx 资源不存在
    public static final int RESOURCE_NOT_FOUND = 40401;
    public static final int USER_NOT_FOUND = 40402;

    // 409xx 冲突
    public static final int CONFLICT = 40900;
    public static final int PARTICIPANTS_FULL = 40901;
    public static final int ALREADY_REGISTERED = 40902;
    public static final int REGISTRATION_CLOSED = 40903;

    // 429xx 频率限制
    public static final int LOGIN_LOCKED = 42901;

    // 500xx 服务端错误
    public static final int SERVER_ERROR = 50000;
}
