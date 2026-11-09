package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.TitleDict;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.TitleDictMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 院系/职称字典维护的单元测试。
 *
 * <p>重点是"新增时自动生成编号并回填到实体"这一行为：
 * 前端依赖返回文案里的编号做提示，编号没回填就会显示 null。
 */
class AdminDictServiceImplTest {

    private DepartmentMapper departmentMapper;
    private TitleDictMapper titleDictMapper;
    private NumberGenerator numberGenerator;
    private AdminDictServiceImpl service;

    @BeforeEach
    void setUp() {
        departmentMapper = mock(DepartmentMapper.class);
        titleDictMapper = mock(TitleDictMapper.class);
        numberGenerator = mock(NumberGenerator.class);
        service = new AdminDictServiceImpl(departmentMapper, titleDictMapper, numberGenerator);
    }

    @Test
    @DisplayName("新增院系：生成编号、回填并落库")
    void addDepartment() {
        when(numberGenerator.generateDeptNo()).thenReturn("D001");
        Department dept = new Department();

        String msg = service.addDepartment(dept);

        assertEquals("D001", dept.getDeptNo());
        verify(departmentMapper).insert(dept);
        assertEquals("院系添加成功！院系号为：D001", msg);
    }

    @Test
    @DisplayName("新增职称：生成编号、回填并落库")
    void addTitle() {
        when(numberGenerator.generateTitleNo()).thenReturn("TT001");
        TitleDict title = new TitleDict();

        String msg = service.addTitle(title);

        assertEquals("TT001", title.getTitleNo());
        verify(titleDictMapper).insert(title);
        assertEquals("职称添加成功！职称号为：TT001", msg);
    }

    @Test
    @DisplayName("列表与删除直接委托 Mapper")
    void listAndDelete() {
        when(departmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(titleDictMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertTrue(service.listDepartments().isEmpty());
        assertTrue(service.listTitles().isEmpty());

        service.deleteDepartment("D001");
        service.deleteTitle("TT001");

        verify(departmentMapper).deleteById("D001");
        verify(titleDictMapper).deleteById("TT001");
    }
}
