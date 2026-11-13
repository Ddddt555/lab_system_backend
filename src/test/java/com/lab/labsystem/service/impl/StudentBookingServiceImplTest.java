package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.service.student.impl.StudentBookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentBookingServiceImplTest {

    private StringRedisTemplate redis;
    private RedissonClient redissonClient;
    private RLock lock;
    private StudentBookingServiceImpl service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);

        // 模拟 RedissonClient 返回具体的 RLock
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        service = new StudentBookingServiceImpl();
        // 同时注入 StringRedisTemplate 和 RedissonClient
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
    }

    @Test
    @DisplayName("重复点击触发并发锁时返回业务异常")
    void rejectConcurrentClick() throws InterruptedException {
        // 模拟 Redisson 抢锁失败（返回 false）
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.bookSession("S001", "SE001"));

        assertEquals("您正在预约中，请勿频繁点击！", e.getMessage());
    }

    @Test
    @DisplayName("场次不存在时返回业务异常")
    void rejectMissingSession() throws InterruptedException {
        // 模拟 Redisson 抢锁成功
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        ExpSessionMapper mockSessionMapper = mock(ExpSessionMapper.class);
        ReflectionTestUtils.setField(service, "sessionMapper", mockSessionMapper);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.bookSession("S001", "SE404"));

        assertEquals("该场次不存在！", e.getMessage());
    }
}