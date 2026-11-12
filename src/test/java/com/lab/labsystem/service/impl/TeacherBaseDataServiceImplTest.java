package com.lab.labsystem.service.impl;

import com.lab.labsystem.entity.ExpCourse;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysSemester;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.LabRoomMapper;
import com.lab.labsystem.mapper.SysSemesterMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.teacher.impl.TeacherBaseDataServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 教师端基础数据组装的单元测试。
 *
 * <p>重点是下拉框展示名与成绩权重的默认值：前端直接把这些字符串送进页面，
 * 格式错一个字符都会让下拉框显示异常。
 */
class TeacherBaseDataServiceImplTest {

    private TeachClassMapper teachClassMapper;
    private ExpCourseMapper courseMapper;
    private TeacherBaseDataServiceImpl service;

    @BeforeEach
    void setUp() {
        teachClassMapper = mock(TeachClassMapper.class);
        courseMapper = mock(ExpCourseMapper.class);
        service = new TeacherBaseDataServiceImpl(
                teachClassMapper,
                courseMapper,
                mock(LabRoomMapper.class),
                mock(SysSemesterMapper.class));
    }

    @Test
    @DisplayName("没有教学班时返回空列表")
    void emptyWhenNoClasses() {
        when(teachClassMapper.selectList(any())).thenReturn(Collections.emptyList());
        assertTrue(service.listMyClasses("T001").isEmpty());
    }

    @Test
    @DisplayName("展示名格式为 班号 - 课程名 (学期)")
    void displayNameFormat() {
        TeachClass tc = new TeachClass();
        tc.setClassNo("C001");
        tc.setCourseNo("CO1");
        tc.setSemester("2025-2026-1");
        when(teachClassMapper.selectList(any())).thenReturn(Collections.singletonList(tc));

        ExpCourse course = new ExpCourse();
        course.setCourseNo("CO1");
        course.setCourseName("数据结构");
        when(courseMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(course));

        List<Map<String, String>> result = service.listMyClasses("T001");

        assertEquals(1, result.size());
        assertEquals("C001 - 数据结构 (2025-2026-1)", result.get(0).get("displayName"));
    }

    @Test
    @DisplayName("成绩权重缺省为 30/70，已配置时原样返回")
    void weightDefaults() {
        TeachClass withoutWeight = new TeachClass();
        withoutWeight.setClassNo("C001");
        withoutWeight.setCourseNo("CO1");
        withoutWeight.setSemester("2025-2026-1");

        TeachClass withWeight = new TeachClass();
        withWeight.setClassNo("C002");
        withWeight.setCourseNo("CO1");
        withWeight.setSemester("2025-2026-1");
        withWeight.setDailyWeight(40);
        withWeight.setReportWeight(60);

        when(teachClassMapper.selectList(any())).thenReturn(Arrays.asList(withoutWeight, withWeight));

        ExpCourse course = new ExpCourse();
        course.setCourseNo("CO1");
        course.setCourseName("数据结构");
        when(courseMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(course));

        List<Map<String, String>> result = service.listMyClasses("T001");

        assertEquals("30", result.get(0).get("dailyWeight"));
        assertEquals("70", result.get(0).get("reportWeight"));
        assertEquals("40", result.get(1).get("dailyWeight"));
        assertEquals("60", result.get(1).get("reportWeight"));
    }

    @Test
    @DisplayName("课程缺失时展示名回退为“未知课程”")
    void unknownCourseFallback() {
        TeachClass tc = new TeachClass();
        tc.setClassNo("C001");
        tc.setCourseNo("CO404");
        tc.setSemester("2025-2026-1");
        when(teachClassMapper.selectList(any())).thenReturn(Collections.singletonList(tc));
        when(courseMapper.selectBatchIds(any())).thenReturn(Collections.emptyList());

        List<Map<String, String>> result = service.listMyClasses("T001");

        assertEquals("C001 - 未知课程 (2025-2026-1)", result.get(0).get("displayName"));
    }
}
