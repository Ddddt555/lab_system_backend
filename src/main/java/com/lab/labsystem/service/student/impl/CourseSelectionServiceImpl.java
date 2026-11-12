package com.lab.labsystem.service.student.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.ExpCourse;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.StudentBooking;
import com.lab.labsystem.entity.StudentTeachClass;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.student.CourseSelectionService;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CourseSelectionServiceImpl implements CourseSelectionService {

    private final TeachClassMapper teachClassMapper;
    private final ExpCourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final StudentTeachClassMapper studentTeachClassMapper;
    private final ExpProjectMapper projectMapper;
    private final ExpSessionMapper sessionMapper;
    private final StudentBookingMapper bookingMapper;

    public CourseSelectionServiceImpl(TeachClassMapper teachClassMapper,
                                      ExpCourseMapper courseMapper,
                                      TeacherMapper teacherMapper,
                                      StudentTeachClassMapper studentTeachClassMapper,
                                      ExpProjectMapper projectMapper,
                                      ExpSessionMapper sessionMapper,
                                      StudentBookingMapper bookingMapper) {
        this.teachClassMapper = teachClassMapper;
        this.courseMapper = courseMapper;
        this.teacherMapper = teacherMapper;
        this.studentTeachClassMapper = studentTeachClassMapper;
        this.projectMapper = projectMapper;
        this.sessionMapper = sessionMapper;
        this.bookingMapper = bookingMapper;
    }

    @Override
    public Object listAvailableClasses(String studentNo, Integer page, Integer size) {
        boolean paged = page != null && size != null;

        List<TeachClass> allClasses;
        long total = 0;
        if (paged) {
            Page<TeachClass> tcPage = teachClassMapper.selectPage(new Page<>(page, size), null);
            allClasses = tcPage.getRecords();
            total = tcPage.getTotal();
        } else {
            // 无分页参数时也不能整表返回，统一走上限
            allClasses = QueryLimits.list(teachClassMapper, null);
        }

        if (allClasses.isEmpty()) {
            return paged ? new PageResult<>(0, new ArrayList<>()) : new ArrayList<>();
        }

        // 批量查询，消灭 N+1
        List<String> classNos = allClasses.stream().map(TeachClass::getClassNo).collect(Collectors.toList());
        List<String> courseNos = allClasses.stream().map(TeachClass::getCourseNo).distinct().collect(Collectors.toList());
        List<String> workNos = allClasses.stream().map(TeachClass::getWorkNo).distinct().collect(Collectors.toList());

        Map<String, String> courseMap = courseNos.isEmpty() ? new HashMap<>() :
                courseMapper.selectBatchIds(courseNos).stream()
                        .collect(Collectors.toMap(ExpCourse::getCourseNo, ExpCourse::getCourseName, (k1, k2) -> k1));

        Map<String, String> teacherMap = workNos.isEmpty() ? new HashMap<>() :
                teacherMapper.selectBatchIds(workNos).stream()
                        .collect(Collectors.toMap(Teacher::getWorkNo, Teacher::getName, (k1, k2) -> k1));

        List<StudentTeachClass> selectedList = studentTeachClassMapper.selectList(
                new QueryWrapper<StudentTeachClass>().eq("student_no", studentNo).in("class_no", classNos));
        Set<String> selectedClassNos = selectedList.stream()
                .map(StudentTeachClass::getClassNo).collect(Collectors.toSet());

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (TeachClass tc : allClasses) {
            Map<String, Object> map = new HashMap<>();
            map.put("classNo", tc.getClassNo());
            map.put("semester", tc.getSemester());
            map.put("courseName", courseMap.getOrDefault(tc.getCourseNo(), "未知课程"));
            map.put("teacherName", teacherMap.getOrDefault(tc.getWorkNo(), "未知教师"));
            map.put("isSelected", selectedClassNos.contains(tc.getClassNo()));
            resultList.add(map);
        }

        return paged ? new PageResult<>(total, resultList) : resultList;
    }

    @Override
    public String selectClass(String studentNo, String classNo) {
        QueryWrapper<StudentTeachClass> qw = new QueryWrapper<StudentTeachClass>()
                .eq("student_no", studentNo)
                .eq("class_no", classNo);
        if (studentTeachClassMapper.selectCount(qw) > 0) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "你已经加入该教学班了，请勿重复操作！");
        }

        StudentTeachClass stc = new StudentTeachClass();
        stc.setStudentNo(studentNo);
        stc.setClassNo(classNo);

        TeachClass tc = teachClassMapper.selectById(classNo);
        if (tc != null) {
            stc.setSemester(tc.getSemester());
        }

        studentTeachClassMapper.insert(stc);
        return "选课成功！";
    }

    @Override
    public String dropClass(String studentNo, String classNo) {
        List<ExpProject> projects = projectMapper.selectList(
                new QueryWrapper<ExpProject>().eq("class_no", classNo));
        if (!projects.isEmpty()) {
            List<String> projectNos = projects.stream().map(ExpProject::getProjectNo).toList();
            List<ExpSession> sessions = sessionMapper.selectList(
                    new QueryWrapper<ExpSession>().in("project_no", projectNos));
            if (!sessions.isEmpty()) {
                Set<String> sessionNos = new HashSet<>(
                        sessions.stream().map(ExpSession::getSessionNo).toList());
                QueryWrapper<StudentBooking> bookQw = new QueryWrapper<StudentBooking>()
                        .eq("student_no", studentNo).eq("status", 1).in("session_no", sessionNos);
                if (bookingMapper.selectCount(bookQw) > 0) {
                    throw BusinessException.of(ErrorCode.BAD_REQUEST, "你已预约了该班级下的实验场次，请先取消预约再退课！");
                }
            }
        }

        QueryWrapper<StudentTeachClass> qw = new QueryWrapper<StudentTeachClass>()
                .eq("student_no", studentNo)
                .eq("class_no", classNo);
        studentTeachClassMapper.delete(qw);
        return "退课成功！";
    }
}