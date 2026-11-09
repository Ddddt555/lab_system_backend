package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.StudentTeachClass;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 选课中心业务规则测试。
 *
 * <p>覆盖两条容易被重构改坏的规则：
 * 重复选课拦截、以及“有未取消预约时禁止退课”。
 */
class CourseSelectionServiceImplTest {

    private TeachClassMapper teachClassMapper;
    private StudentTeachClassMapper studentTeachClassMapper;
    private ExpProjectMapper projectMapper;
    private ExpSessionMapper sessionMapper;
    private StudentBookingMapper bookingMapper;
    private CourseSelectionServiceImpl service;

    @BeforeEach
    void setUp() {
        teachClassMapper = mock(TeachClassMapper.class);
        studentTeachClassMapper = mock(StudentTeachClassMapper.class);
        projectMapper = mock(ExpProjectMapper.class);
        sessionMapper = mock(ExpSessionMapper.class);
        bookingMapper = mock(StudentBookingMapper.class);

        service = new CourseSelectionServiceImpl(
                teachClassMapper,
                mock(ExpCourseMapper.class),
                mock(TeacherMapper.class),
                studentTeachClassMapper,
                projectMapper,
                sessionMapper,
                bookingMapper);
    }

    @Test
    @DisplayName("重复选课被拒绝")
    void rejectDuplicateSelection() {
        when(studentTeachClassMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.selectClass("S001", "C001"));

        assertEquals("你已经加入该教学班了，请勿重复操作！", e.getMessage());
        verify(studentTeachClassMapper, never()).insert(any(StudentTeachClass.class));
    }

    @Test
    @DisplayName("选课时带上教学班所属学期")
    void selectClassCopiesSemester() {
        when(studentTeachClassMapper.selectCount(any())).thenReturn(0L);

        TeachClass tc = new TeachClass();
        tc.setClassNo("C001");
        tc.setSemester("2025-2026-1");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        assertEquals("选课成功！", service.selectClass("S001", "C001"));

        ArgumentCaptor<StudentTeachClass> captor = ArgumentCaptor.forClass(StudentTeachClass.class);
        verify(studentTeachClassMapper).insert(captor.capture());
        assertEquals("S001", captor.getValue().getStudentNo());
        assertEquals("2025-2026-1", captor.getValue().getSemester());
    }

    @Test
    @DisplayName("存在有效预约时禁止退课")
    void rejectDropWhenBooked() {
        ExpProject project = new ExpProject();
        project.setProjectNo("P001");
        when(projectMapper.selectList(any())).thenReturn(Collections.singletonList(project));

        ExpSession session = new ExpSession();
        session.setSessionNo("SE001");
        when(sessionMapper.selectList(any())).thenReturn(Collections.singletonList(session));

        when(bookingMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.dropClass("S001", "C001"));

        assertEquals("你已预约了该班级下的实验场次，请先取消预约再退课！", e.getMessage());
        verify(studentTeachClassMapper, never()).delete(any());
    }

    @Test
    @DisplayName("没有场次预约时允许退课")
    void allowDropWithoutBooking() {
        when(projectMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertEquals("退课成功！", service.dropClass("S001", "C001"));
        verify(studentTeachClassMapper).delete(any());
    }
}
