package com.lab.labsystem.common;

/**
 * 业务校验失败。
 *
 * <p>用于 service 层向调用方表达“请求不合法/不被允许”，
 * 由 GlobalExceptionHandler 中 {@code @ExceptionHandler(RuntimeException.class)}
 * 统一转成 {@code Result.error(message)}，前端拿到与原 controller 直接
 * {@code return Result.error(...)} 完全一致的响应。
 *
 * <p>单独定义一个类型（而不是复用 IllegalArgumentException）的原因：
 * 有些方法内部有大范围的 {@code catch (Exception e)} 用于清理资源，
 * 需要能把这些“预期内的业务拒绝”原样抛出去，而不是被当成未知异常包装一遍。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
