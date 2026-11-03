package com.lab.labsystem.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC; // 🛑【核心新增】引入 MDC 获取 Trace ID
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private boolean isDev() {
        return "dev".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);
    }

    // 1. 处理所有未被捕获的系统级异常
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        // 🛑 记录完整的错误堆栈
        log.error("系统发生未捕获异常: ", e);
        
        // 🛑【核心新增】获取当前的 Trace ID
        String traceId = MDC.get("traceId");
        String traceInfo = traceId != null ? "（追踪码: " + traceId + "）" : "";

        if (isDev()) {
            return Result.error("服务器开小差了(DEV)：" + e.getMessage() + traceInfo);
        } else {
            return Result.error("服务器开小差了，请联系管理员并提供追踪码" + traceInfo);
        }
    }

    // 2. 处理空指针异常（单独兜底）
    @ExceptionHandler(NullPointerException.class)
    public Result<String> handleNullPointerException(NullPointerException e) {
        log.error("触发空指针异常: ", e);
        
        String traceId = MDC.get("traceId");
        String traceInfo = traceId != null ? "（追踪码: " + traceId + "）" : "";
        
        if (isDev()) {
            return Result.error("操作失败(DEV)：系统检测到空数据异常！" + traceInfo);
        }
        return Result.error("操作失败：系统处理数据时遇到了一点小问题！" + traceInfo);
    }

    // 3. 处理业务逻辑异常（适用于你手动 throw new RuntimeException 的情况）
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        // 业务提示不需要打印完整堆栈，只记录 warn 级别即可
        log.warn("业务逻辑异常: {}", e.getMessage());
        return Result.error(e.getMessage());
    }

    // 4. 处理数据库唯一索引冲突（如并发重复预约、重复插入等）
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<String> handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据库唯一约束冲突: {}", e.getMessage());
        return Result.error("操作失败：您已经提交过了，请勿重复操作！");
    }

    // 5. 处理 @Valid 参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationExceptions(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null) {
            log.warn("参数校验失败: {}", fieldError.getDefaultMessage());
            return Result.error(fieldError.getDefaultMessage());
        }
        return Result.error("参数校验失败，请检查输入！");
    }

    // 6. 处理 @Validated 或者表单提交的绑定异常（兜底）
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null) {
            log.warn("参数绑定失败: {}", fieldError.getDefaultMessage());
            return Result.error(fieldError.getDefaultMessage());
        }
        return Result.error("参数绑定失败，请检查输入！");
    }
}