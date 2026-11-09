package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.TitleDict;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.TitleDictMapper;
import com.lab.labsystem.service.AdminDictService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminDictServiceImpl implements AdminDictService {

    private final DepartmentMapper departmentMapper;
    private final TitleDictMapper titleDictMapper;
    private final NumberGenerator numberGenerator;

    public AdminDictServiceImpl(DepartmentMapper departmentMapper,
                                TitleDictMapper titleDictMapper,
                                NumberGenerator numberGenerator) {
        this.departmentMapper = departmentMapper;
        this.titleDictMapper = titleDictMapper;
        this.numberGenerator = numberGenerator;
    }

    @Override
    public List<Department> listDepartments() {
        return departmentMapper.selectList(null);
    }

    @Override
    public String addDepartment(Department dept) {
        dept.setDeptNo(numberGenerator.generateDeptNo());
        departmentMapper.insert(dept);
        return "院系添加成功！院系号为：" + dept.getDeptNo();
    }

    @Override
    public void deleteDepartment(String deptNo) {
        departmentMapper.deleteById(deptNo);
    }

    @Override
    public List<TitleDict> listTitles() {
        return titleDictMapper.selectList(null);
    }

    @Override
    public String addTitle(TitleDict title) {
        title.setTitleNo(numberGenerator.generateTitleNo());
        titleDictMapper.insert(title);
        return "职称添加成功！职称号为：" + title.getTitleNo();
    }

    @Override
    public void deleteTitle(String titleNo) {
        titleDictMapper.deleteById(titleNo);
    }
}
