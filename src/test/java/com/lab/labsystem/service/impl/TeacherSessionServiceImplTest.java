package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.dto.request.SessionCreateDTO;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysSemester;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.LabRoomMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysSemesterMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排课规则的单元测试。
 *
 * <p>排课有三重冲突校验（教师 / 实验室 / 教学班）和一次时间换算，
 * 任一条漏掉都会造成真实的教学事故，这里逐条覆盖。
 */
class TeacherSessionServiceImplTest {

    private ExpProjectMapper projectMapper;
    private TeachClassMapper teachClassMapper;
    private LabRoomMapper labRoomMapper;
    private ExpSessionMapper sessionMapper;
    private SysSemesterMapper semesterMapper;
    private NumberGenerator numberGenerator;
    private TeacherSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        projectMapper = mock(ExpProjectMapper.class);
        teachClassMapper = mock(TeachClassMapper.class);
        labRoomMapper = mock(LabRoomMapper.class);
        sessionMapper = mock(ExpSessionMapper.class);
        semesterMapper = mock(SysSemesterMapper.class);
        numberGenerator = mock(NumberGenerator.class);

        service = new TeacherSessionServiceImpl(
                projectMapper,
                teachClassMapper,
                labRoomMapper,
                sessionMapper,
                semesterMapper,
                mock(StudentBookingMapper.class),
                mock(StudentMapper.class),
                numberGenerator);
    }

    private static SessionCreateDTO request(String projectNo, String roomNo, int capacity) {
        SessionCreateDTO dto = new SessionCreateDTO();
        dto.setProjectNo(projectNo);
        dto.setRoomNo(roomNo);
        dto.setCapacity(capacity);
        dto.setSemester("2025-2026-1");
        dto.setWeekNum(3);
        dto.setDayOfWeek(2);
        dto.setPeriod("3-4节");
        return dto;
    }

    private static ExpProject project(String classNo) {
        ExpProject p = new ExpProject();
        p.setProjectNo("P001");
        p.setClassNo(classNo);
        return p;
    }

    private static TeachClass teachClass(String workNo) {
        TeachClass tc = new TeachClass();
        tc.setClassNo("C001");
        tc.setWorkNo(workNo);
        return tc;
    }

    private static LabRoom room(int capacity) {
        LabRoom r = new LabRoom();
        r.setRoomNo("R001");
        r.setRoomName("一号实验室");
        r.setCapacity(capacity);
        return r;
    }

    @Test
    @DisplayName("项目不存在时拒绝排课")
    void rejectMissingProject() {
        when(projectMapper.selectById("P404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P404", "R001", 30), "T001"));

        assertEquals("实验项目不存在！", e.getMessage());
        verify(sessionMapper, never()).insert(any(ExpSession.class));
    }

    @Test
    @DisplayName("不能给其他教师的项目排课")
    void rejectOtherTeacherProject() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T999"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P001", "R001", 30), "T001"));

        assertEquals("非法操作：您无权安排其他老师的实验项目！", e.getMessage());
    }

    @Test
    @DisplayName("实验室不存在时拒绝排课")
    void rejectMissingRoom() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P001", "R404", 30), "T001"));

        assertEquals("实验室不存在！", e.getMessage());
    }

    @Test
    @DisplayName("排课容量不能超过实验室容量")
    void rejectCapacityOverflow() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R001")).thenReturn(room(20));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P001", "R001", 30), "T001"));

        assertEquals("排课容量不能超过实验室容量！", e.getMessage());
    }

    @Test
    @DisplayName("教师同一时段已有排课")
    void rejectTeacherConflict() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R001")).thenReturn(room(50));

        ExpSession exist = new ExpSession();
        exist.setWorkNo("T001");
        when(sessionMapper.selectList(any())).thenReturn(Collections.singletonList(exist));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P001", "R001", 30), "T001"));

        assertEquals("排课失败：您在【第3周 周二 3-4节】已有其他排课！", e.getMessage());
    }

    @Test
    @DisplayName("实验室同一时段已被占用")
    void rejectRoomConflict() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R001")).thenReturn(room(50));

        ExpSession exist = new ExpSession();
        exist.setWorkNo("T999");
        exist.setRoomNo("R001");
        when(sessionMapper.selectList(any())).thenReturn(Collections.singletonList(exist));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P001", "R001", 30), "T001"));

        assertEquals("排课失败：实验室【一号实验室】在【第3周 周二 3-4节】已被占用！", e.getMessage());
    }

    @Test
    @DisplayName("同一教学班在此时段已有排课")
    void rejectClassConflict() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R001")).thenReturn(room(50));

        ExpSession exist = new ExpSession();
        exist.setWorkNo("T999");
        exist.setRoomNo("R999");
        exist.setProjectNo("P999");
        when(sessionMapper.selectList(any())).thenReturn(Collections.singletonList(exist));

        ExpProject other = new ExpProject();
        other.setProjectNo("P999");
        other.setClassNo("C001"); // 与当前项目同班
        when(projectMapper.selectById("P999")).thenReturn(other);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P001", "R001", 30), "T001"));

        assertEquals("排课失败：该教学班在此时段已经有排课了！", e.getMessage());
    }

    @Test
    @DisplayName("排课成功：生成场次号、换算绝对时间、剩余座位等于容量")
    void addSessionSuccess() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R001")).thenReturn(room(50));
        when(sessionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(numberGenerator.generateSessionNo()).thenReturn("SE2025001");

        SysSemester semester = new SysSemester();
        semester.setSemesterName("2025-2026-1");
        semester.setStartDate(LocalDate.of(2025, 9, 1));
        when(semesterMapper.selectById("2025-2026-1")).thenReturn(semester);

        SessionCreateDTO req = request("P001", "R001", 30);
        String msg = service.addSession(req, "T001");

        assertEquals("场次安排成功！场次号为：SE2025001", msg);

        // 捕获传给 Mapper 的实体，验证由后端接管生成的字段
        ArgumentCaptor<ExpSession> captor = ArgumentCaptor.forClass(ExpSession.class);
        verify(sessionMapper).insert(captor.capture());
        
        ExpSession saved = captor.getValue();
        assertEquals("T001", saved.getWorkNo());
        assertEquals("SE2025001", saved.getSessionNo());
        assertEquals(30, saved.getRemainingSeats());
        // 第 3 周 周二 3-4 节 → 2025-09-01 + 2 周 + 1 天 = 09-16，10:00
        assertEquals(LocalDateTime.of(2025, 9, 16, 10, 0), saved.getSessionTime());
    }

    @Test
    @DisplayName("学期配置缺失时拒绝排课")
    void rejectMissingSemester() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R001")).thenReturn(room(50));
        when(sessionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(numberGenerator.generateSessionNo()).thenReturn("SE2025001");
        when(semesterMapper.selectById("2025-2026-1")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P001", "R001", 30), "T001"));

        assertEquals("排课失败：该学期配置不存在！", e.getMessage());
        verify(sessionMapper, never()).insert(any(ExpSession.class));
    }

    @Test
    @DisplayName("不能查看/取消其他教师的场次")
    void rejectOtherTeacherSessionOperations() {
        ExpSession exist = new ExpSession();
        exist.setWorkNo("T999");
        when(sessionMapper.selectById("SE1")).thenReturn(exist);

        assertEquals("非法操作：您无权查看！",
                assertThrows(BusinessException.class,
                        () -> service.listSessionBookings("SE1", "T001")).getMessage());

        assertEquals("非法操作！",
                assertThrows(BusinessException.class,
                        () -> service.deleteSession("SE1", "T001")).getMessage());

        verify(sessionMapper, never()).deleteById(anyString());
    }
}