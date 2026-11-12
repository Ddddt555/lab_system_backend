package com.lab.labsystem.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.dto.request.TeachClassCreateDTO;
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
import com.lab.labsystem.service.admin.TeachingService;
import com.lab.labsystem.common.NumberGenerator;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
            // 无分页参数时，限制最大返回条数，防止全表加载
            classes = QueryLimits.list(teachClassMapper, null);
        }

        if (classes.isEmpty()) {
            return (page != null && size != null) ? new PageResult<>(total, new ArrayList<>()) : new ArrayList<>();
        }

        // 1. 批量查询课程
        List<String> courseNos = classes.stream()
                .map(TeachClass::getCourseNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, ExpCourse> courseMap = courseNos.isEmpty() ? new HashMap<>() :
                courseMapper.selectBatchIds(courseNos).stream()
                        .collect(Collectors.toMap(ExpCourse::getCourseNo, c -> c, (k1, k2) -> k1));

        // 2. 批量查询教师
        List<String> workNos = classes.stream()
                .map(TeachClass::getWorkNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Teacher> teacherMap = workNos.isEmpty() ? new HashMap<>() :
                teacherMapper.selectBatchIds(workNos).stream()
                        .collect(Collectors.toMap(Teacher::getWorkNo, t -> t, (k1, k2) -> k1));

        // 3. 组装数据（零查库）
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (TeachClass tc : classes) {
            Map<String, Object> map = new HashMap<>();
            map.put("classNo", tc.getClassNo());
            map.put("courseNo", tc.getCourseNo());
            map.put("workNo", tc.getWorkNo());
            map.put("semester", tc.getSemester());

            ExpCourse course = courseMap.get(tc.getCourseNo());
            map.put("courseName", course != null ? course.getCourseName() : "未知课程");

            Teacher teacher = teacherMap.get(tc.getWorkNo());
            map.put("teacherName", teacher != null ? teacher.getName() : "未知教师");

            resultList.add(map);
        }

        return (page != null && size != null) ? new PageResult<>(total, resultList) : resultList;
    }

    @Override
    public String addTeachClass(TeachClassCreateDTO dto) {
        TeachClass teachClass = new TeachClass();
        
        // 1. 安全拷贝
        BeanUtils.copyProperties(dto, teachClass);
        
        // 2. 后端接管敏感字段
        teachClass.setClassNo(numberGenerator.generateClassNo(dto.getCourseNo()));
        teachClass.setDeleted(0);
        
        teachClassMapper.insert(teachClass);
        return "教学班创建成功！班级号为：" + teachClass.getClassNo();
    }

    @Override
    public void deleteTeachClass(String classNo) {
        // 引用检查：教学班被实验项目或选课记录引用时不允许删除
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