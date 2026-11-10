package com.lab.labsystem.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private boolean isDev() {
        return "dev".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);
    }

    // 1. 处理自定义的业务异常（预期内的错误，直接返回给用户提示）
    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        // 业务异常不需要打印堆栈，只记录 warn 即可
        log.warn("业务逻辑异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    // 2. 处理其他运行时异常（非预期内的错误，如空指针、数据库报错等）
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);
        
        String traceId = MDC.get("traceId");
        String traceInfo = traceId != null ? "（追踪码: " + traceId + "）" : "";

        if (isDev()) {
            return Result.error("运行时异常(DEV)：" + e.getMessage() + traceInfo);
        }
        // 生产环境隐藏具体报错，防止敏感信息泄露
        return Result.error("操作失败，系统繁忙，请稍后重试" + traceInfo);
    }

    // 3. 处理所有未被捕获的 Exception 级异常
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统发生未捕获异常: ", e);
        
        String traceId = MDC.get("traceId");
        String traceInfo = traceId != null ? "（追踪码: " + traceId + "）" : "";

        if (isDev()) {
            return Result.error("服务器开小差了(DEV)：" + e.getMessage() + traceInfo);
        }
        return Result.error("服务器开小差了，请联系管理员并提供追踪码" + traceInfo);
    }

    // 4. 处理 404 找不到资源异常（比如访问了不存在的接口或静态文件）
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<String> handleNotFound(NoResourceFoundException e) {
        // 这类异常通常是扫描器或者敲错地址，不需要打印完整的 Error 堆栈刷屏
        log.warn("请求的资源不存在: {}", e.getMessage());
        return Result.error("请求的资源不存在");
    }

    // 5. 处理数据库唯一索引冲突（如并发重复预约、重复插入等）
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<String> handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据库唯一约束冲突: {}", e.getMessage());
        return Result.error("操作失败：您已经提交过了，请勿重复操作！");
    }

    // 6. 处理 @Valid 参数校验异常（JSON Body）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationExceptions(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null) {
            log.warn("参数校验失败: {}", fieldError.getDefaultMessage());
            return Result.error(fieldError.getDefaultMessage());
        }
        return Result.error("参数校验失败，请检查输入！");
    }

    // 7. 处理表单提交的绑定异常
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null) {
            log.warn("参数绑定失败: {}", fieldError.getDefaultMessage());
            return Result.error(fieldError.getDefaultMessage());
        }
        return Result.error("参数绑定失败，请检查输入！");
    }

    // 8. 处理上传文件超限异常
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Result<String> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传超过大小限制: {}", e.getMessage());
        return Result.error("上传失败：文件大小超出了系统允许的上限（单文件最大 50MB）！");
    }

    // 9. 处理请求方法不支持异常（比如用 GET 请求 POST 接口）
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<String> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方式错误: {}", e.getMessage());
        return Result.error("操作失败：请求方式不被支持！");
    }

    // 10. 处理接口参数缺失异常
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<String> handleMissingParamException(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return Result.error("操作失败：缺少必要参数 [" + e.getParameterName() + "]！");
    }
}