package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.StudentBooking;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.WaitlistRecordMapper;
import com.lab.labsystem.service.student.impl.WaitlistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaitlistServiceImplTest {

    private ExpSessionMapper sessionMapper;
    private ExpProjectMapper projectMapper;
    private StudentTeachClassMapper studentTeachClassMapper;
    private StudentBookingMapper bookingMapper;
    private WaitlistRecordMapper waitlistRecordMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ListOperations<String, String> listOps;
    private TransactionTemplate transactionTemplate;
    private WaitlistServiceImpl service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(ExpSessionMapper.class);
        projectMapper = mock(ExpProjectMapper.class);
        studentTeachClassMapper = mock(StudentTeachClassMapper.class);
        bookingMapper = mock(StudentBookingMapper.class);
        waitlistRecordMapper = mock(WaitlistRecordMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        transactionTemplate = mock(TransactionTemplate.class);

        when(stringRedisTemplate.opsForList()).thenReturn(listOps);

        // 模拟 TransactionTemplate 直接同步执行回调，保持异常向外抛出
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        service = new WaitlistServiceImpl();
        ReflectionTestUtils.setField(service, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "studentTeachClassMapper", studentTeachClassMapper);
        ReflectionTestUtils.setField(service, "bookingMapper", bookingMapper);
        ReflectionTestUtils.setField(service, "waitlistRecordMapper", waitlistRecordMapper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "transactionTemplate", transactionTemplate);
    }

    @Test
    @DisplayName("队列为空时直接返回，不进行出队操作")
    void emptyQueue() {
        when(listOps.size(anyString())).thenReturn(0L);

        service.processWaitlist("SE001");

        verify(listOps, never()).leftPop(anyString());
    }

    @Test
    @DisplayName("正常补位：扣减座位、插入预约、更新候补状态")
    void successAutoBook() {
        when(listOps.size(anyString())).thenReturn(1L);
        when(listOps.leftPop(anyString())).thenReturn("S001");

        ExpSession session = new ExpSession();
        session.setSessionNo("SE001");
        session.setProjectNo("P001");
        session.setSemester("2025-2026-1");
        session.setWeekNum(1);
        session.setDayOfWeek(1);
        session.setPeriod("1-2节");
        when(sessionMapper.selectById("SE001")).thenReturn(session);
        when(sessionMapper.update(isNull(), any())).thenReturn(1);

        ExpProject project = new ExpProject();
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        when(studentTeachClassMapper.selectCount(any())).thenReturn(1L);
        when(bookingMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.processWaitlist("SE001");

        verify(bookingMapper).insert(any(StudentBooking.class));
        verify(waitlistRecordMapper).update(isNull(), any());
        verify(stringRedisTemplate).delete("waitlist:user:SE001:S001");
    }

    @Test
    @DisplayName("业务异常（如退课、时间冲突）淘汰该用户，更新为 FAILED")
    void businessExceptionRemovesUser() {
        when(listOps.size(anyString())).thenReturn(1L);
        when(listOps.leftPop(anyString())).thenReturn("S001");

        when(sessionMapper.selectById("SE001")).thenReturn(new ExpSession());
        when(projectMapper.selectById(any())).thenReturn(new ExpProject());
        // 模拟未加入教学班触发业务异常
        when(studentTeachClassMapper.selectCount(any())).thenReturn(0L);

        service.processWaitlist("SE001");

        verify(stringRedisTemplate).delete("waitlist:user:SE001:S001");
        verify(waitlistRecordMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("系统异常（如数据库死锁）将该用户重新放回队尾")
    void systemExceptionPushesBack() {
        when(listOps.size(anyString())).thenReturn(1L);
        when(listOps.leftPop(anyString())).thenReturn("S001");

        // 模拟数据库异常
        when(sessionMapper.selectById("SE001")).thenThrow(new RuntimeException("DB Error"));

        service.processWaitlist("SE001");

        verify(listOps).rightPush("waitlist:queue:SE001", "S001");
        verify(stringRedisTemplate, never()).delete("waitlist:user:SE001:S001");
    }
}