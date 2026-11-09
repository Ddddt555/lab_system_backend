package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.mapper.AdminClassMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.StudentMapper;
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
 * 组织架构删除的引用检查测试。
 *
 * <p>删专业会连带让行政班悬空，删行政班会让学生悬空，两者都必须先拦住。
 */
class OrgServiceImplTest {

    private MajorMapper majorMapper;
    private AdminClassMapper adminClassMapper;
    private StudentMapper studentMapper;
    private OrgServiceImpl service;

    @BeforeEach
    void setUp() {
        majorMapper = mock(MajorMapper.class);
        adminClassMapper = mock(AdminClassMapper.class);
        studentMapper = mock(StudentMapper.class);

        service = new OrgServiceImpl();
        ReflectionTestUtils.setField(service, "majorMapper", majorMapper);
        ReflectionTestUtils.setField(service, "adminClassMapper", adminClassMapper);
        ReflectionTestUtils.setField(service, "studentMapper", studentMapper);
    }

    @Test
    @DisplayName("专业下还有行政班时拒绝删除")
    void rejectDeleteMajorWithClasses() {
        when(adminClassMapper.selectCount(any())).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteMajor("M001"));

        assertEquals("该专业下还有 2 个行政班，请先处理后再删除！", e.getMessage());
        verify(majorMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("行政班下还有学生时拒绝删除")
    void rejectDeleteAdminClassWithStudents() {
        when(studentMapper.selectCount(any())).thenReturn(5L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteAdminClass("AC001"));

        assertEquals("该行政班下还有 5 名学生，请先处理后再删除！", e.getMessage());
        verify(adminClassMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("无引用时正常删除")
    void deleteWhenNoReferences() {
        when(adminClassMapper.selectCount(any())).thenReturn(0L);
        when(studentMapper.selectCount(any())).thenReturn(0L);

        service.deleteMajor("M001");
        service.deleteAdminClass("AC001");

        verify(majorMapper).deleteById("M001");
        verify(adminClassMapper).deleteById("AC001");
    }
}
