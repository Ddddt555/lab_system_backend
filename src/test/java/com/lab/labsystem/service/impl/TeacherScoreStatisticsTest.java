package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpScore;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.StudentBooking;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpScoreMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 教师端成绩统计规则的单元测试。
 *
 * <p>这些规则原先写在 {@code TeacherController} 里，只能靠跑起整个 Spring 上下文验证。
 */
class TeacherScoreStatisticsTest {

    private TeachClassMapper teachClassMapper;
    private ExpScoreMapper scoreMapper;
    private ExpProjectMapper projectMapper;
    private ExpSessionMapper sessionMapper;
    private StudentBookingMapper bookingMapper;
    private TeacherScoreServiceImpl service;

    @BeforeEach
    void setUp() {
        teachClassMapper = mock(TeachClassMapper.class);
        scoreMapper = mock(ExpScoreMapper.class);
        projectMapper = mock(ExpProjectMapper.class);
        sessionMapper = mock(ExpSessionMapper.class);
        bookingMapper = mock(StudentBookingMapper.class);

        service = new TeacherScoreServiceImpl();
        ReflectionTestUtils.setField(service, "teachClassMapper", teachClassMapper);
        ReflectionTestUtils.setField(service, "scoreMapper", scoreMapper);
        ReflectionTestUtils.setField(service, "projectMapper", projectMapper);
        ReflectionTestUtils.setField(service, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "bookingMapper", bookingMapper);
    }

    private static TeachClass teachClass(String classNo) {
        TeachClass tc = new TeachClass();
        tc.setClassNo(classNo);
        return tc;
    }

    private static ExpScore score(double total) {
        ExpScore s = new ExpScore();
        s.setTotalScore(total);
        return s;
    }

    @Test
    @DisplayName("没有教学班时各项统计为 0")
    void distributionWithoutClasses() {
        when(teachClassMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Integer> result = service.getScoreDistribution("T001");

        assertEquals(5, result.size());
        assertEquals(0, result.get("优秀"));
        assertEquals(0, result.get("不及格"));
    }

    @Test
    @DisplayName("成绩分布按 90/80/70/60 分段统计")
    void distributionCountsBands() {
        when(teachClassMapper.selectList(any())).thenReturn(Collections.singletonList(teachClass("C1")));
        when(scoreMapper.selectList(any())).thenReturn(Arrays.asList(
                score(95), score(90),   // 优秀
                score(85),              // 良好
                score(75),              // 中等
                score(60),              // 及格
                score(59)));            // 不及格

        Map<String, Integer> result = service.getScoreDistribution("T001");

        assertEquals(2, result.get("优秀"));
        assertEquals(1, result.get("良好"));
        assertEquals(1, result.get("中等"));
        assertEquals(1, result.get("及格"));
        assertEquals(1, result.get("不及格"));
    }

    @Test
    @DisplayName("没有项目时开出率为 0")
    void experimentRateWithoutProjects() {
        when(teachClassMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(projectMapper.selectCount(any())).thenReturn(0L);
        when(sessionMapper.selectList(any())).thenReturn(Collections.emptyList());

        Map<String, Object> result = service.getExperimentRate("T001");

        assertEquals(0L, result.get("total"));
        assertEquals(0L, result.get("opened"));
        assertEquals("0.0", result.get("rate"));
    }

    @Test
    @DisplayName("开出率 = 已开场次所属项目数 / 项目总数")
    void experimentRateComputes() {
        when(teachClassMapper.selectList(any())).thenReturn(Collections.singletonList(teachClass("C1")));
        when(projectMapper.selectCount(any())).thenReturn(4L);

        ExpSession s1 = new ExpSession();
        s1.setProjectNo("P1");
        ExpSession s2 = new ExpSession();
        s2.setProjectNo("P1"); // 同一项目开两场，只算一个
        ExpSession s3 = new ExpSession();
        s3.setProjectNo("P2");
        when(sessionMapper.selectList(any())).thenReturn(Arrays.asList(s1, s2, s3));

        Map<String, Object> result = service.getExperimentRate("T001");

        assertEquals(4L, result.get("total"));
        assertEquals(2L, result.get("opened"));
        assertEquals("50.0", result.get("rate"));
    }

    @Test
    @DisplayName("教学班没有项目时成绩列表返回空")
    void courseScoresEmptyWhenNoProjects() {
        when(projectMapper.selectList(any())).thenReturn(Collections.emptyList());

        Object result = service.getCourseScores("T001", "C1", null, null);

        assertInstanceOf(List.class, result);
        assertTrue(((List<?>) result).isEmpty());
    }

    @Test
    @DisplayName("分页越界时返回空记录但总数正确")
    void courseScoresPageOutOfRange() {
        ExpProject project = new ExpProject();
        project.setProjectNo("P1");
        when(projectMapper.selectList(any())).thenReturn(Collections.singletonList(project));

        ExpSession session = new ExpSession();
        session.setSessionNo("SE1");
        when(sessionMapper.selectList(any())).thenReturn(Collections.singletonList(session));

        StudentBooking b1 = new StudentBooking();
        b1.setStudentNo("S1");
        StudentBooking b2 = new StudentBooking();
        b2.setStudentNo("S2");
        when(bookingMapper.selectList(any())).thenReturn(Arrays.asList(b1, b2));

        // 共 2 名学生，请求第 5 页（每页 10 条）必然越界
        Object result = service.getCourseScores("T001", "C1", 5, 10);

        assertInstanceOf(PageResult.class, result);
        PageResult<?> page = (PageResult<?>) result;
        assertEquals(2L, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }
}
