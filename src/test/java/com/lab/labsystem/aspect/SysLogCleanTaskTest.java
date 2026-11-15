package com.lab.labsystem.aspect;

import com.lab.labsystem.mapper.SysLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @DisplayName("抢到锁时按时间条件进行物理删除，并释放锁")
    void deletesExpiredLogs() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(logMapper.physicalDeleteOlderThan(any())).thenReturn(42);

        task.cleanExpiredLogs();

        // 验证调用了物理删除，且传入的时间参数在 180 天前左右
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(logMapper).physicalDeleteOlderThan(captor.capture());
        
        LocalDateTime expectedCutoff = LocalDateTime.now().minusDays(180);
        assertTrue(captor.getValue().isAfter(expectedCutoff.minusMinutes(1)));
        assertTrue(captor.getValue().isBefore(expectedCutoff.plusMinutes(1)));

        verify(lock).unlock();
    }

    @Test
    @DisplayName("抢不到锁时跳过，不执行删除")
    void skipsWhenLockNotAcquired() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        task.cleanExpiredLogs();

        verify(logMapper, never()).physicalDeleteOlderThan(any());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("保留天数配置非法时跳过，避免误删全表")
    void skipsWhenRetentionInvalid() throws Exception {
        ReflectionTestUtils.setField(task, "retentionDays", 0);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        task.cleanExpiredLogs();

        verify(logMapper, never()).physicalDeleteOlderThan(any());
    }
}