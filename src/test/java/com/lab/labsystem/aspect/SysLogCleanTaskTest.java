package com.lab.labsystem.aspect;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.SysLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统日志保留期清理的单元测试。
 *
 * <p>重点验证三件事：多实例下抢不到锁不执行、保留天数非法时不执行、
 * 以及删除条件确实是按时间切（而不是整表清空）。
 */
class SysLogCleanTaskTest {

    private SysLogMapper logMapper;
    private RLock lock;
    private SysLogCleanTask task;

    @BeforeEach
    void setUp() {
        logMapper = mock(SysLogMapper.class);
        lock = mock(RLock.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        task = new SysLogCleanTask();
        ReflectionTestUtils.setField(task, "logMapper", logMapper);
        ReflectionTestUtils.setField(task, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(task, "retentionDays", 180);
    }

    @Test
    @DisplayName("抢到锁时按时间条件删除，并释放锁")
    void deletesExpiredLogs() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(logMapper.delete(any())).thenReturn(42);

        task.cleanExpiredLogs();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<SysLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(logMapper).delete(captor.capture());
        // 必须是按 create_time 过滤，而不是无条件清空
        assertTrue(captor.getValue().getSqlSegment().contains("create_time"));

        verify(lock).unlock();
    }

    @Test
    @DisplayName("抢不到锁时跳过，不执行删除")
    void skipsWhenLockNotAcquired() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        task.cleanExpiredLogs();

        verify(logMapper, never()).delete(any());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("保留天数配置非法时跳过，避免误删全表")
    void skipsWhenRetentionInvalid() throws Exception {
        ReflectionTestUtils.setField(task, "retentionDays", 0);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        task.cleanExpiredLogs();

        verify(logMapper, never()).delete(any());
    }
}
