package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.service.student.impl.StudentBookingServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 预约入口的业务异常类型测试。
 *
 * <p>预约是学生端最高频的操作，失败原因（重复点击、约满、时间冲突）
 * 必须原样告诉用户，不能被异常处理器当作系统错误脱敏。
 */
class StudentBookingServiceImplTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private StudentBookingServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        service = new StudentBookingServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
    }

    @Test
    @DisplayName("重复点击触发幂等锁时返回业务异常")
    void rejectConcurrentClick() {
        when(ops.setIfAbsent(anyString(), anyString(), eq(5L), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.bookSession("S001", "SE001"));

        assertEquals("您正在预约中，请勿频繁点击！", e.getMessage());
    }

    @Test
    @DisplayName("场次不存在时返回业务异常")
    void rejectMissingSession() {
        when(ops.setIfAbsent(anyString(), anyString(), eq(5L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(redis.delete(anyString())).thenReturn(true);
        ReflectionTestUtils.setField(service, "sessionMapper",
                mock(com.lab.labsystem.mapper.ExpSessionMapper.class));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.bookSession("S001", "SE404"));

        assertEquals("该场次不存在！", e.getMessage());
    }
}
