package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.entity.ExpCourse;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.StudentTeachClass;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.TeachingService;
import com.lab.labsystem.common.NumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeachingServiceImpl implements TeachingService {
    @Autowired private TeachClassMapper teachClassMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private StudentTeachClassMapper studentTeachClassMapper;
    @Autowired private ExpCourseMapper courseMapper;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private NumberGenerator numberGenerator;

    @Override
    public Object getTeachClassList(Integer page, Integer size) {
        List<TeachClass> classes;
        long total = 0;
        if (page != null && size != null) {
            Page<TeachClass> classPage = teachClassMapper.selectPage(new Page<>(page, size), null);
            classes = classPage.getRecords();
            total = classPage.getTotal();
        } else {
            classes = teachClassMapper.selectList(null);
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (TeachClass tc : classes) {
            Map<String, Object> map = new HashMap<>();
            map.put("classNo", tc.getClassNo());
            map.put("courseNo", tc.getCourseNo());
            map.put("workNo", tc.getWorkNo());
            map.put("semester", tc.getSemester());
            ExpCourse course = courseMapper.selectById(tc.getCourseNo());
            map.put("courseName", course != null ? course.getCourseName() : "未知课程");
            Teacher teacher = teacherMapper.selectById(tc.getWorkNo());
            map.put("teacherName", teacher != null ? teacher.getName() : "未知教师");
            resultList.add(map);
        }
        if (page != null && size != null) return new PageResult<>(total, resultList);
        return resultList;
    }

    @Override
    public String addTeachClass(TeachClass teachClass) {
        teachClass.setClassNo(numberGenerator.generateClassNo(teachClass.getCourseNo()));
        teachClassMapper.insert(teachClass);
        return "教学班创建成功！班级号为：" + teachClass.getClassNo();
    }

    @Override
    public void deleteTeachClass(String classNo) {
        // 【引用检查】教学班被实验项目或选课记录引用时不允许删除
        long projects = projectMapper.selectCount(
                new QueryWrapper<ExpProject>().eq("class_no", classNo));
        if (projects > 0) {
            throw new BusinessException("该教学班下还有 " + projects + " 个实验项目，请先处理后再删除！");
        }
        long selections = studentTeachClassMapper.selectCount(
                new QueryWrapper<StudentTeachClass>().eq("class_no", classNo));
        if (selections > 0) {
            throw new BusinessException("该教学班下还有 " + selections + " 名学生的选课记录，请先处理后再删除！");
        }
        teachClassMapper.deleteById(classNo);
    }
}