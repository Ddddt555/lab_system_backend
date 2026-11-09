package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.ExpCourse;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysSemester;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.LabRoomMapper;
import com.lab.labsystem.mapper.SysSemesterMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.TeacherBaseDataService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeacherBaseDataServiceImpl implements TeacherBaseDataService {

    private final TeachClassMapper teachClassMapper;
    private final ExpCourseMapper courseMapper;
    private final LabRoomMapper labRoomMapper;
    private final SysSemesterMapper semesterMapper;

    public TeacherBaseDataServiceImpl(TeachClassMapper teachClassMapper,
                                      ExpCourseMapper courseMapper,
                                      LabRoomMapper labRoomMapper,
                                      SysSemesterMapper semesterMapper) {
        this.teachClassMapper = teachClassMapper;
        this.courseMapper = courseMapper;
        this.labRoomMapper = labRoomMapper;
        this.semesterMapper = semesterMapper;
    }

    @Override
    public List<Map<String, String>> listMyClasses(String workNo) {
        List<TeachClass> classes = teachClassMapper.selectList(
                new QueryWrapper<TeachClass>().eq("work_no", workNo));
        if (classes.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量查询课程，消灭 N+1
        List<String> courseNos = classes.stream()
                .map(TeachClass::getCourseNo).distinct().collect(Collectors.toList());
        Map<String, String> courseMap = courseNos.isEmpty() ? new HashMap<>() :
                courseMapper.selectBatchIds(courseNos).stream()
                        .collect(Collectors.toMap(ExpCourse::getCourseNo, ExpCourse::getCourseName, (k1, k2) -> k1));

        List<Map<String, String>> resultList = new ArrayList<>();
        for (TeachClass tc : classes) {
            Map<String, String> map = new HashMap<>();
            map.put("classNo", tc.getClassNo());
            String courseName = courseMap.getOrDefault(tc.getCourseNo(), "未知课程");
            map.put("displayName", tc.getClassNo() + " - " + courseName + " (" + tc.getSemester() + ")");
            map.put("dailyWeight", String.valueOf(tc.getDailyWeight() != null ? tc.getDailyWeight() : 30));
            map.put("reportWeight", String.valueOf(tc.getReportWeight() != null ? tc.getReportWeight() : 70));
            resultList.add(map);
        }
        return resultList;
    }

    @Override
    public List<LabRoom> listLabs() {
        return labRoomMapper.selectList(null);
    }

    @Override
    public List<SysSemester> listSemesters() {
        return semesterMapper.selectList(null);
    }
}
