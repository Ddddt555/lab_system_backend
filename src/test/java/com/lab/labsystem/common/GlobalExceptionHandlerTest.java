package com.lab.labsystem.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理的契约测试。
 * 验证不同的异常类型是否能被正确转换为统一的错误码与响应格式。
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
    @DisplayName("业务异常应原样返回提示，且状态码为 400")
    void businessMessagePassesThrough() {
        setProfile("prod");

        Result<String> result = handler.handleBusinessException(
                new BusinessException("手慢了，该场次已被约满！"));

        assertEquals(400, result.getCode());
        assertEquals("手慢了，该场次已被约满！", result.getMsg());
    }

    @Test
    @DisplayName("生产环境下普通运行时异常应被脱敏，且状态码为 500")
    void runtimeExceptionMaskedInProd() {
        setProfile("prod");

        Result<String> result = handler.handleRuntimeException(
                new RuntimeException("ORA-00942: table or view does not exist"));

        assertEquals(500, result.getCode());
        assertFalse(result.getMsg().contains("ORA-00942"),
                "生产环境不应把底层异常细节返回给前端");
        assertTrue(result.getMsg().contains("系统繁忙"));
    }

    @Test
    @DisplayName("开发环境下普通运行时异常保留原始信息，便于排查")
    void runtimeExceptionVisibleInDev() {
        setProfile("dev");

        Result<String> result = handler.handleRuntimeException(
                new RuntimeException("空指针演示"));

        assertTrue(result.getMsg().contains("空指针演示"));
    }

    @Test
    @DisplayName("开发环境下业务异常不额外加前缀")
    void businessMessageCleanInDev() {
        setProfile("dev");

        Result<String> result = handler.handleBusinessException(
                new BusinessException("该场次不存在！"));

        assertEquals("该场次不存在！", result.getMsg());
    }
}