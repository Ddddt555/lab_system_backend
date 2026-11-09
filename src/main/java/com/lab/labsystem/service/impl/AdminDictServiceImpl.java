package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.Major;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.entity.TitleDict;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.mapper.TitleDictMapper;
import com.lab.labsystem.service.AdminDictService;
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
    public String addDepartment(Department dept) {
        dept.setDeptNo(numberGenerator.generateDeptNo());
        departmentMapper.insert(dept);
        return "院系添加成功！院系号为：" + dept.getDeptNo();
    }

    @Override
    public void deleteDepartment(String deptNo) {
        // 【引用检查】专业、教师都挂在院系下，直接删除会产生悬空引用
        long majors = majorMapper.selectCount(new QueryWrapper<Major>().eq("dept_no", deptNo));
        if (majors > 0) {
            throw new BusinessException("该院系下还有 " + majors + " 个专业，请先处理后再删除！");
        }
        long teachers = teacherMapper.selectCount(new QueryWrapper<Teacher>().eq("dept_no", deptNo));
        if (teachers > 0) {
            throw new BusinessException("该院系下还有 " + teachers + " 名教师，请先处理后再删除！");
        }
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
        long teachers = teacherMapper.selectCount(new QueryWrapper<Teacher>().eq("title_no", titleNo));
        if (teachers > 0) {
            throw new BusinessException("该职称下还有 " + teachers + " 名教师，请先处理后再删除！");
        }
        titleDictMapper.deleteById(titleNo);
    }
}
