package com.lab.labsystem.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    private boolean isDev() {
        return "dev".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);
    }

    // 专门处理 ResponseStatusException，保留其原本的 HTTP 状态码
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result<String>> handleResponseStatusException(ResponseStatusException e) {
        log.warn("请求状态异常: {}", e.getMessage());
        return ResponseEntity.status(e.getStatusCode())
                .body(Result.error(e.getStatusCode().value(), e.getReason()));
    }

    // 1. 处理自定义的业务异常（预期内的错误）
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<String>> handleBusinessException(BusinessException e) {
        log.warn("业务逻辑异常: [{}] {}", e.getCode(), e.getMessage());
        
        HttpStatus httpStatus = HttpStatus.BAD_REQUEST; // 默认 400
        if (e.getCode() == 401 || e.getCode() == 40101) {
            httpStatus = HttpStatus.UNAUTHORIZED; // 401
        } else if (e.getCode() == 403) {
            httpStatus = HttpStatus.FORBIDDEN; // 403
        } else if (e.getCode() == 404 || e.getCode() == 40005) {
            httpStatus = HttpStatus.NOT_FOUND; // 404
        } else if (e.getCode() == 429) {
            httpStatus = HttpStatus.TOO_MANY_REQUESTS; // 429
        } else if (e.getCode() >= 500) {
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR; // 500
        }

        return ResponseEntity.status(httpStatus).body(Result.error(e.getCode(), e.getMessage()));
    }

    // 2. 处理其他运行时异常（非预期内的错误，如空指针、数据库报错等）
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<String>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);
        
        String traceId = MDC.get("traceId");
        String traceInfo = traceId != null ? "（追踪码: " + traceId + "）" : "";
        String msg = isDev() ? "运行时异常(DEV)：" + e.getMessage() + traceInfo : "操作失败，系统繁忙，请稍后重试" + traceInfo;

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCode.SYSTEM_ERROR.getCode(), msg));
    }

    // 3. 处理所有未被捕获的 Exception 级异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleException(Exception e) {
        log.error("系统发生未捕获异常: ", e);
        
        String traceId = MDC.get("traceId");
        String traceInfo = traceId != null ? "（追踪码: " + traceId + "）" : "";
        String msg = isDev() ? "服务器开小差了(DEV)：" + e.getMessage() + traceInfo : "服务器开小差了，请联系管理员并提供追踪码" + traceInfo;

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(ErrorCode.SYSTEM_ERROR.getCode(), msg));
    }

    // 4. 处理 404 找不到资源异常
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<String>> handleNotFound(NoResourceFoundException e) {
        log.warn("请求的资源不存在: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(404, "请求的资源不存在"));
    }

    // 5. 处理数据库唯一索引冲突（如并发重复预约、重复插入等）
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<String>> handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据库唯一约束冲突: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(ErrorCode.CONCURRENT_CONFLICT.getCode(), "操作失败：您已经提交过了，请勿重复操作！"));
    }

    // 6. 处理 @Valid 参数校验异常（JSON Body）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<String>> handleValidationExceptions(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败，请检查输入！";
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.BAD_REQUEST.getCode(), msg));
    }

    // 7. 处理表单提交的绑定异常
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<String>> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败，请检查输入！";
        log.warn("参数绑定失败: {}", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.BAD_REQUEST.getCode(), msg));
    }

    // 8. 处理上传文件超限异常
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<String>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("文件上传超过大小限制: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(ErrorCode.BAD_REQUEST.getCode(), "上传失败：文件大小超出了系统允许的上限（单文件最大 50MB）！"));
    }

    // 9. 处理请求方法不支持异常
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<String>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方式错误: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error(ErrorCode.BAD_REQUEST.getCode(), "操作失败：请求方式不被支持！"));
    }

    // 10. 处理接口参数缺失异常
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<String>> handleMissingParamException(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(ErrorCode.BAD_REQUEST.getCode(), "操作失败：缺少必要参数 [" + e.getParameterName() + "]！"));
    }
}