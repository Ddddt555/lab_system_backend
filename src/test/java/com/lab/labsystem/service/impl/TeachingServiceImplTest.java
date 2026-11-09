package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 教学班删除的引用检查测试。
 *
 * <p>删除教学班会同时让实验项目和学生的选课记录失去归属，两者都要先拦住。
 */
class TeachingServiceImplTest {

    private TeachClassMapper teachClassMapper;
    private ExpProjectMapper projectMapper;
    private StudentTeachClassMapper studentTeachClassMapper;
    private TeachingServiceImpl service;

    @BeforeEach
    void setUp() {
        teachClassMapper = mock(TeachClassMapper.class);
        projectMapper = mock(ExpProjectMapper.class);
        studentTeachClassMapper = mock(StudentTeachClassMapper.class);

        service = new TeachingServiceImpl();
        ReflectionTestUtils.setField(service, "teachClassMapper", teachClassMapper);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "studentTeachClassMapper", studentTeachClassMapper);
    }

    @Test
    @DisplayName("教学班下还有实验项目时拒绝删除")
    void rejectDeleteWithProjects() {
        when(projectMapper.selectCount(any())).thenReturn(3L);
        when(studentTeachClassMapper.selectCount(any())).thenReturn(0L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteTeachClass("C001"));

        assertEquals("该教学班下还有 3 个实验项目，请先处理后再删除！", e.getMessage());
        verify(teachClassMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("教学班下还有学生选课记录时拒绝删除")
    void rejectDeleteWithSelections() {
        when(projectMapper.selectCount(any())).thenReturn(0L);
        when(studentTeachClassMapper.selectCount(any())).thenReturn(8L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteTeachClass("C001"));

        assertEquals("该教学班下还有 8 名学生的选课记录，请先处理后再删除！", e.getMessage());
        verify(teachClassMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("无引用时正常删除")
    void deleteWhenNoReferences() {
        when(projectMapper.selectCount(any())).thenReturn(0L);
        when(studentTeachClassMapper.selectCount(any())).thenReturn(0L);

        service.deleteTeachClass("C001");

        verify(teachClassMapper).deleteById("C001");
    }
}
