package com.lab.labsystem.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理的契约测试。
 * 验证不同的异常类型是否能被正确转换为真实的 HTTP 状态码与内部统一错误码。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    private void setProfile(String profile) {
        ReflectionTestUtils.setField(handler, "activeProfile", profile);
    }

    @Test
    @DisplayName("业务异常应原样返回提示，HTTP 状态码为 400，内部 code 为 400")
    void businessMessagePassesThrough() {
        setProfile("prod");

        ResponseEntity<Result<String>> response = handler.handleBusinessException(
                new BusinessException("手慢了，该场次已被约满！"));

        // 验证外层真实的 HTTP 状态码
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        // 验证内层统一错误码体系
        Result<String> result = response.getBody();
        assertNotNull(result);
        assertEquals(400, result.getCode());
        assertEquals("手慢了，该场次已被约满！", result.getMsg());
    }

    @Test
    @DisplayName("特定业务码应映射为对应的 HTTP 状态码（如 403）")
    void specificCodeMapsToHttpStatus() {
        setProfile("prod");

        ResponseEntity<Result<String>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.FORBIDDEN.getCode(), "非法操作：您无权执行此操作！"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        
        Result<String> result = response.getBody();
        assertNotNull(result);
        assertEquals(403, result.getCode());
    }

    @Test
    @DisplayName("生产环境下普通运行时异常应被脱敏，HTTP 状态码为 500")
    void runtimeExceptionMaskedInProd() {
        setProfile("prod");

        ResponseEntity<Result<String>> response = handler.handleRuntimeException(
                new RuntimeException("ORA-00942: table or view does not exist"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        Result<String> result = response.getBody();
        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertFalse(result.getMsg().contains("ORA-00942"),
                "生产环境不应把底层异常细节返回给前端");
        assertTrue(result.getMsg().contains("系统繁忙"));
    }

    @Test
    @DisplayName("开发环境下普通运行时异常保留原始信息，HTTP 状态码为 500")
    void runtimeExceptionVisibleInDev() {
        setProfile("dev");

        ResponseEntity<Result<String>> response = handler.handleRuntimeException(
                new RuntimeException("空指针演示"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        Result<String> result = response.getBody();
        assertNotNull(result);
        assertTrue(result.getMsg().contains("空指针演示"));
    }

    @Test
    @DisplayName("开发环境下业务异常不额外加前缀，HTTP 状态码为 400")
    void businessMessageCleanInDev() {
        setProfile("dev");

        ResponseEntity<Result<String>> response = handler.handleBusinessException(
                new BusinessException("该场次不存在！"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        Result<String> result = response.getBody();
        assertNotNull(result);
        assertEquals("该场次不存在！", result.getMsg());
    }
}