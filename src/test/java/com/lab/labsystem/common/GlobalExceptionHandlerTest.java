package com.lab.labsystem.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 异常到响应的映射契约测试。
 *
 * <p>这里锁住一条容易被破坏的约定：
 * <b>业务异常（BusinessException）的消息必须原样返回给用户</b>，
 * 而其他运行时异常在生产环境必须被脱敏。
 *
 * <p>之所以要单独测这一层：项目里大量业务规则（预约满员、签到码错误、
 * 无权操作）靠异常消息直接面向用户。一旦常量/处理器改成分支返回通用文案，
 * 用户就只会看到"系统繁忙"，而编译器不会报任何错。
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
    @DisplayName("业务异常的消息原样返回，且 code 为 400")
    void businessMessagePassesThrough() {
        setProfile("prod");

        Result<String> result = handler.handleBusinessException(
                new BusinessException("手慢了，该场次已被约满！"));

        assertEquals(400, result.getCode());
        assertEquals("手慢了，该场次已被约满！", result.getMsg());
    }

    @Test
    @DisplayName("生产环境下普通运行时异常被脱敏")
    void runtimeExceptionMaskedInProd() {
        setProfile("prod");

        Result<String> result = handler.handleRuntimeException(
                new RuntimeException("ORA-00942: table or view does not exist"));

        assertEquals(400, result.getCode());
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
