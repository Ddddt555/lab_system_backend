package com.lab.labsystem.common;

/**
 * 业务异常。
 * 用于在业务流程中抛出预期内的错误，由全局异常处理器统一拦截并返回标准响应。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = ErrorCode.BAD_REQUEST.getCode();
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 使用预定义错误码抛出业务异常。
     */
    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 使用预定义错误码及自定义提示语抛出业务异常。
     */
    public static BusinessException of(ErrorCode errorCode, String customMsg) {
        return new BusinessException(errorCode.getCode(), customMsg);
    }

    public int getCode() {
        return code;
    }
}