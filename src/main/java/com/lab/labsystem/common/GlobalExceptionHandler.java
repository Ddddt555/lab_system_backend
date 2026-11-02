package com.lab.labsystem.common;

import lombok.extern.slf4j.Slf4j; // 🛑 新增
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j // 🛑 开启日志
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
        
        if (isDev()) {
            return Result.error("服务器开小差了(DEV)：" + e.getMessage());
        } else {
            return Result.error("服务器开小差了，请联系管理员！");
        }
    }

    // 2. 处理空指针异常（单独兜底）
    @ExceptionHandler(NullPointerException.class)
    public Result<String> handleNullPointerException(NullPointerException e) {
        log.error("触发空指针异常: ", e);
        if (isDev()) {
            return Result.error("操作失败(DEV)：系统检测到空数据异常！");
        }
        return Result.error("操作失败：系统处理数据时遇到了一点小问题！");
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