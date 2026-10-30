package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.entity.TitleDict;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.mapper.TitleDictMapper;
import com.lab.labsystem.service.TeacherService;
import com.lab.labsystem.common.NumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeacherServiceImpl implements TeacherService {
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private TitleDictMapper titleDictMapper;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private NumberGenerator numberGenerator;

    @Override
    public Object getTeacherList(Integer page, Integer size) {
        List<Teacher> teachers;
        long total = 0;
        if (page != null && size != null) {
            Page<Teacher> teacherPage = teacherMapper.selectPage(new Page<>(page, size), null);
            teachers = teacherPage.getRecords();
            total = teacherPage.getTotal();
        } else {
            teachers = teacherMapper.selectList(null);
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Teacher t : teachers) {
            Map<String, Object> map = new HashMap<>();
            map.put("workNo", t.getWorkNo());
            map.put("name", t.getName());
            map.put("deptNo", t.getDeptNo());
            map.put("titleNo", t.getTitleNo());
            Department dept = departmentMapper.selectById(t.getDeptNo());
            map.put("deptName", dept != null ? dept.getDeptName() : "未分配");
            TitleDict title = titleDictMapper.selectById(t.getTitleNo());
            map.put("titleName", title != null ? title.getTitleName() : "未分配");
            resultList.add(map);
        }
        if (page != null && size != null) return new PageResult<>(total, resultList);
        return resultList;
    }

    @Override
    public String addTeacher(Teacher teacher) {
        teacher.setWorkNo(numberGenerator.generateWorkNo());
        if (teacher.getPassword() == null || teacher.getPassword().isEmpty()) {
            teacher.setPassword("123456");
        }
        teacher.setPassword(passwordEncoder.encode(teacher.getPassword()));

        // ⚠️ 核心：检查工号是否被逻辑删除占用了
        Teacher existTeacher = teacherMapper.selectRealById(teacher.getWorkNo());
        
        if (existTeacher != null) {
            // 如果存在，复活并覆盖更新
            teacherMapper.restoreTeacher(teacher.getWorkNo());
            teacher.setDeleted(0);
            teacherMapper.updateById(teacher);
            return "工号 " + teacher.getWorkNo() + " 曾被删除，已为您恢复并更新！";
        }

        // 如果不存在，正常插入
        teacherMapper.insert(teacher);
        return "教师添加成功！工号为：" + teacher.getWorkNo();
    }
    
    @Override
    public void deleteTeacher(String workNo) {
        teacherMapper.deleteById(workNo);
    }
    
}