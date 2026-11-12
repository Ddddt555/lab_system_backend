package com.lab.labsystem.common;

/**
 * 系统统一错误码。
 * 采用区间划分：
 * 200: 成功
 * 400xx: 客户端参数或业务逻辑错误
 * 401xx: 认证错误
 * 403xx: 权限错误
 * 500xx: 服务器内部错误
 */
public enum ErrorCode {
    // 通用
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    SYSTEM_ERROR(500, "服务器开小差了，请稍后重试"),

    // 认证
    UNAUTHORIZED(401, "未登录或登录已过期"),
    LOGIN_FAILED(40001, "账号或密码错误"),
    CAPTCHA_ERROR(40002, "验证码错误或已过期"),
    ACCOUNT_LOCKED(40003, "账号已被锁定，请稍后再试"),
    TOKEN_INVALID(40101, "凭证无效或已过期"),

    // 权限
    FORBIDDEN(403, "权限不足，无权访问"),
    
    // 业务
    FILE_INVALID(40004, "文件格式非法"),
    DATA_NOT_FOUND(40005, "请求的数据不存在"),
    CONCURRENT_CONFLICT(40006, "操作过于频繁，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}