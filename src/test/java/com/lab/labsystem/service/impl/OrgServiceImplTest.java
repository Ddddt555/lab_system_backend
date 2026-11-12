package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.dto.request.AdminClassCreateDTO;
import com.lab.labsystem.dto.request.MajorCreateDTO;
import com.lab.labsystem.entity.AdminClass;
import com.lab.labsystem.entity.Major;
import com.lab.labsystem.mapper.AdminClassMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.StudentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    private NumberGenerator numberGenerator;
    private OrgServiceImpl service;

    @BeforeEach
    void setUp() {
        majorMapper = mock(MajorMapper.class);
        adminClassMapper = mock(AdminClassMapper.class);
        studentMapper = mock(StudentMapper.class);
        numberGenerator = mock(NumberGenerator.class);

        service = new OrgServiceImpl();
        ReflectionTestUtils.setField(service, "majorMapper", majorMapper);
        ReflectionTestUtils.setField(service, "adminClassMapper", adminClassMapper);
        ReflectionTestUtils.setField(service, "studentMapper", studentMapper);
        ReflectionTestUtils.setField(service, "numberGenerator", numberGenerator);
    }

    @Test
    @DisplayName("新增专业：生成主键并落库")
    void addMajor() {
        when(numberGenerator.generateMajorNo()).thenReturn("M001");
        MajorCreateDTO dto = new MajorCreateDTO();
        dto.setMajorName("计算机科学");
        dto.setDeptNo("D001");

        String msg = service.addMajor(dto);

        assertEquals("专业添加成功！专业号为：M001", msg);

        ArgumentCaptor<Major> captor = ArgumentCaptor.forClass(Major.class);
        verify(majorMapper).insert(captor.capture());
        Major saved = captor.getValue();
        assertEquals("M001", saved.getMajorNo());
        assertEquals("计算机科学", saved.getMajorName());
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