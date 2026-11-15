package com.lab.labsystem.common;

/**
 * 业务异常。
 * 用于在业务流程中抛出预期内的错误，由全局异常处理器统一拦截并返回标准响应。
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final ErrorCode errorCode;

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.BAD_REQUEST.getCode();
        this.errorCode = ErrorCode.BAD_REQUEST;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null; // 兼容直接传 int code 的情况
    }

    public BusinessException(ErrorCode errorCode, String customMsg) {
        super(customMsg);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }

    public static BusinessException of(ErrorCode errorCode, String customMsg) {
        return new BusinessException(errorCode, customMsg);
    }

    public int getCode() {
        return code;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}