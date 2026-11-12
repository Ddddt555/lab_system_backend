package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.dto.request.DepartmentCreateDTO;
import com.lab.labsystem.dto.request.TitleCreateDTO;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.TitleDict;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.mapper.TitleDictMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 院系/职称字典维护的单元测试。
 *
 * <p>除了编号回填，重点覆盖删除前的引用检查：
 * 院系下挂着专业或教师、职称下挂着教师时，都必须拒绝删除。
 */
class AdminDictServiceImplTest {

    private DepartmentMapper departmentMapper;
    private TitleDictMapper titleDictMapper;
    private MajorMapper majorMapper;
    private TeacherMapper teacherMapper;
    private NumberGenerator numberGenerator;
    private AdminDictServiceImpl service;

    @BeforeEach
    void setUp() {
        departmentMapper = mock(DepartmentMapper.class);
        titleDictMapper = mock(TitleDictMapper.class);
        majorMapper = mock(MajorMapper.class);
        teacherMapper = mock(TeacherMapper.class);
        numberGenerator = mock(NumberGenerator.class);

        service = new AdminDictServiceImpl(
                departmentMapper, titleDictMapper, majorMapper, teacherMapper, numberGenerator);

        // 默认无引用，个别用例再覆盖
        when(majorMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("新增院系：生成编号、回填并落库")
    void addDepartment() {
        when(numberGenerator.generateDeptNo()).thenReturn("D001");
        DepartmentCreateDTO dto = new DepartmentCreateDTO();
        dto.setDeptName("测试学院");

        String msg = service.addDepartment(dto);

        assertEquals("院系添加成功！院系号为：D001", msg);

        // 捕获传给 Mapper 的实体，验证主键是否被正确注入
        ArgumentCaptor<Department> captor = ArgumentCaptor.forClass(Department.class);
        verify(departmentMapper).insert(captor.capture());
        
        Department savedDept = captor.getValue();
        assertEquals("D001", savedDept.getDeptNo());
        assertEquals("测试学院", savedDept.getDeptName());
    }

    @Test
    @DisplayName("新增职称：生成编号、回填并落库")
    void addTitle() {
        when(numberGenerator.generateTitleNo()).thenReturn("TT001");
        TitleCreateDTO dto = new TitleCreateDTO();
        dto.setTitleName("教授");

        String msg = service.addTitle(dto);

        assertEquals("职称添加成功！职称号为：TT001", msg);

        // 捕获传给 Mapper 的实体
        ArgumentCaptor<TitleDict> captor = ArgumentCaptor.forClass(TitleDict.class);
        verify(titleDictMapper).insert(captor.capture());
        
        TitleDict savedTitle = captor.getValue();
        assertEquals("TT001", savedTitle.getTitleNo());
        assertEquals("教授", savedTitle.getTitleName());
    }

    @Test
    @DisplayName("院系下还有专业时拒绝删除")
    void rejectDeleteDepartmentWithMajors() {
        when(majorMapper.selectCount(any())).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteDepartment("D001"));

        assertEquals("该院系下还有 3 个专业，请先处理后再删除！", e.getMessage());
        verify(departmentMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("院系下还有教师时拒绝删除")
    void rejectDeleteDepartmentWithTeachers() {
        when(majorMapper.selectCount(any())).thenReturn(0L);
        when(teacherMapper.selectCount(any())).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteDepartment("D001"));

        assertEquals("该院系下还有 2 名教师，请先处理后再删除！", e.getMessage());
        verify(departmentMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("职称下有教师时拒绝删除")
    void rejectDeleteTitleWithTeachers() {
        when(teacherMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteTitle("TT001"));

        assertEquals("该职称下还有 1 名教师，请先处理后再删除！", e.getMessage());
        verify(titleDictMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("无引用时正常删除")
    void deleteWhenNoReferences() {
        service.deleteDepartment("D001");
        service.deleteTitle("TT001");

        verify(departmentMapper).deleteById("D001");
        verify(titleDictMapper).deleteById("TT001");
    }

    @Test
    @DisplayName("列表与删除直接委托 Mapper")
    void listDelegates() {
        when(departmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(titleDictMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertTrue(service.listDepartments().isEmpty());
        assertTrue(service.listTitles().isEmpty());
    }
}