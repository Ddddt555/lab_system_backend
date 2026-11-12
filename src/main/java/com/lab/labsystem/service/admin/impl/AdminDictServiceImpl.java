package com.lab.labsystem.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.dto.request.DepartmentCreateDTO;
import com.lab.labsystem.dto.request.TitleCreateDTO;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.Major;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.entity.TitleDict;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.mapper.TitleDictMapper;
import com.lab.labsystem.service.admin.AdminDictService;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminDictServiceImpl implements AdminDictService {

    private final DepartmentMapper departmentMapper;
    private final TitleDictMapper titleDictMapper;
    private final MajorMapper majorMapper;
    private final TeacherMapper teacherMapper;
    private final NumberGenerator numberGenerator;

    public AdminDictServiceImpl(DepartmentMapper departmentMapper,
                                TitleDictMapper titleDictMapper,
                                MajorMapper majorMapper,
                                TeacherMapper teacherMapper,
                                NumberGenerator numberGenerator) {
        this.departmentMapper = departmentMapper;
        this.titleDictMapper = titleDictMapper;
        this.majorMapper = majorMapper;
        this.teacherMapper = teacherMapper;
        this.numberGenerator = numberGenerator;
    }

    @Override
    public List<Department> listDepartments() {
        return departmentMapper.selectList(null);
    }

    @Override
    public String addDepartment(DepartmentCreateDTO dto) {
        Department dept = new Department();
        
        // 1. 安全拷贝：只拷贝 dto 中的 deptName
        BeanUtils.copyProperties(dto, dept);
        
        // 2. 后端接管敏感字段
        dept.setDeptNo(numberGenerator.generateDeptNo());
        dept.setDeleted(0);
        
        departmentMapper.insert(dept);
        return "院系添加成功！院系号为：" + dept.getDeptNo();
    }

    @Override
    public void deleteDepartment(String deptNo) {
        // 引用检查：专业、教师都挂在院系下，直接删除会产生悬空引用
        long majors = majorMapper.selectCount(new QueryWrapper<Major>().eq("dept_no", deptNo));
        if (majors > 0) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "该院系下还有 " + majors + " 个专业，请先处理后再删除！");
        }
        long teachers = teacherMapper.selectCount(new QueryWrapper<Teacher>().eq("dept_no", deptNo));
        if (teachers > 0) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "该院系下还有 " + teachers + " 名教师，请先处理后再删除！");
        }
        departmentMapper.deleteById(deptNo);
    }

    @Override
    public List<TitleDict> listTitles() {
        return titleDictMapper.selectList(null);
    }

    @Override
    public String addTitle(TitleCreateDTO dto) {
        TitleDict title = new TitleDict();
        
        // 1. 安全拷贝：只拷贝 dto 中的 titleName
        BeanUtils.copyProperties(dto, title);
        
        // 2. 后端接管敏感字段
        title.setTitleNo(numberGenerator.generateTitleNo());
        title.setDeleted(0);
        
        titleDictMapper.insert(title);
        return "职称添加成功！职称号为：" + title.getTitleNo();
    }

    @Override
    public void deleteTitle(String titleNo) {
        long teachers = teacherMapper.selectCount(new QueryWrapper<Teacher>().eq("title_no", titleNo));
        if (teachers > 0) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "该职称下还有 " + teachers + " 名教师，请先处理后再删除！");
        }
        titleDictMapper.deleteById(titleNo);
    }
}