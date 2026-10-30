package com.lab.labsystem.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ⚠️ 读取当前环境标识，例如 dev, prod, test
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private boolean isDev() {
        return "dev".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);
    }

    // 1. 处理所有未被捕获的系统级异常
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        // 不管什么环境，后台控制台必须打印完整堆栈，方便你排查 Bug
        e.printStackTrace();
        
        if (isDev()) {
            // 开发环境：把详细错误返回给前端，方便你调试
            return Result.error("服务器开小差了(DEV)：" + e.getMessage());
        } else {
            // 生产环境：绝对不暴露底层异常信息
            return Result.error("服务器开小差了，请联系管理员！");
        }
    }

    // 2. 处理空指针异常（单独兜底）
    @ExceptionHandler(NullPointerException.class)
    public Result<String> handleNullPointerException(NullPointerException e) {
        e.printStackTrace();
        if (isDev()) {
            return Result.error("操作失败(DEV)：系统检测到空数据异常！");
        }
        return Result.error("操作失败：系统处理数据时遇到了一点小问题！");
    }

    // 3. 处理业务逻辑异常（适用于你手动 throw new RuntimeException 的情况）
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e) {
        // 我们自己抛出的 RuntimeException 通常是业务提示，可以直接返回
        return Result.error(e.getMessage());
    }

    // 4. 处理数据库唯一索引冲突（如并发重复预约、重复插入等）
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<String> handleDuplicateKeyException(DuplicateKeyException e) {
        // 记录一下日志，方便后端排查
        System.err.println("数据库唯一约束冲突: " + e.getMessage());
        
        // 给前端返回友好提示，不要暴露数据库字段名
        return Result.error("操作失败：您已经提交过了，请勿重复操作！");
    }
}