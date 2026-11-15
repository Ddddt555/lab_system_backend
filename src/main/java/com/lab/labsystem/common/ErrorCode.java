package com.lab.labsystem.common;

import org.springframework.http.HttpStatus;

/**
 * 系统统一错误码。
 * 采用区间划分，并强类型绑定对应的 HTTP 状态码。
 */
public enum ErrorCode {
    // 通用
    SUCCESS(200, "操作成功", HttpStatus.OK),
    BAD_REQUEST(400, "请求参数错误", HttpStatus.BAD_REQUEST),
    SYSTEM_ERROR(500, "服务器开小差了，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),

    // 认证
    UNAUTHORIZED(401, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    LOGIN_FAILED(40001, "账号或密码错误", HttpStatus.BAD_REQUEST),
    CAPTCHA_ERROR(40002, "验证码错误或已过期", HttpStatus.BAD_REQUEST),
    ACCOUNT_LOCKED(40003, "账号已被锁定，请稍后再试", HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(40101, "凭证无效或已过期", HttpStatus.UNAUTHORIZED),

    // 权限
    FORBIDDEN(403, "权限不足，无权访问", HttpStatus.FORBIDDEN),
    
    // 业务
    FILE_INVALID(40004, "文件格式非法", HttpStatus.BAD_REQUEST),
    DATA_NOT_FOUND(40005, "请求的数据不存在", HttpStatus.NOT_FOUND),
    
    // 并发冲突，绑定为 HTTP 409 Conflict
    CONCURRENT_CONFLICT(40006, "操作过于频繁，请稍后再试", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}