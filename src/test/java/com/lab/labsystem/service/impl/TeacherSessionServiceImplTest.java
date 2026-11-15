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
import com.lab.labsystem.service.teacher.impl.TeacherSessionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 排课规则的单元测试。
 */
class TeacherSessionServiceImplTest {

    private ExpProjectMapper projectMapper;
    private TeachClassMapper teachClassMapper;
    private LabRoomMapper labRoomMapper;
    private ExpSessionMapper sessionMapper;
    private SysSemesterMapper semesterMapper;
    private StudentBookingMapper bookingMapper;
    private NumberGenerator numberGenerator;
    private RedissonClient redissonClient;
    private RLock lock;
    private TeacherSessionServiceImpl service;

    @BeforeEach
    void setUp() throws InterruptedException {
        projectMapper = mock(ExpProjectMapper.class);
        teachClassMapper = mock(TeachClassMapper.class);
        labRoomMapper = mock(LabRoomMapper.class);
        sessionMapper = mock(ExpSessionMapper.class);
        semesterMapper = mock(SysSemesterMapper.class);
        bookingMapper = mock(StudentBookingMapper.class);
        numberGenerator = mock(NumberGenerator.class);
        
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);

        // 模拟获取锁成功（注意：去掉了 leaseTime 参数，只 Mock 两个参数的方法）
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        service = new TeacherSessionServiceImpl(
                projectMapper,
                teachClassMapper,
                labRoomMapper,
                sessionMapper,
                semesterMapper,
                bookingMapper,
                mock(StudentMapper.class),
                numberGenerator);
                
        // 注入 RedissonClient
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
    }

    private static SessionCreateDTO request(String projectNo, String roomNo, int capacity) {
        SessionCreateDTO dto = new SessionCreateDTO();
        dto.setProjectNo(projectNo);
        dto.setRoomNo(roomNo);
        dto.setCapacity(capacity);
        dto.setSemester("2030-2031-1");
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

    private void mockBaseValidData() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R001")).thenReturn(room(50));
        
        SysSemester semester = new SysSemester();
        semester.setSemesterName("2030-2031-1");
        semester.setStartDate(LocalDate.of(2030, 9, 1));
        semester.setTotalWeeks(20);
        when(semesterMapper.selectById("2030-2031-1")).thenReturn(semester);
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
    @DisplayName("周次超出学期总周数时拒绝排课")
    void rejectInvalidWeekNum() {
        mockBaseValidData();
        SessionCreateDTO req = request("P001", "R001", 30);
        req.setWeekNum(25);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(req, "T001"));

        assertEquals("排课失败：周次必须在 1 到 20 之间！", e.getMessage());
    }

    @Test
    @DisplayName("非法节次时拒绝排课")
    void rejectInvalidPeriod() {
        mockBaseValidData();
        SessionCreateDTO req = request("P001", "R001", 30);
        req.setPeriod("半夜12点");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(req, "T001"));

        assertEquals("排课失败：非法的节次！", e.getMessage());
    }

    @Test
    @DisplayName("排课时间早于当前时间时拒绝排课")
    void rejectPastTime() {
        mockBaseValidData();
        SessionCreateDTO req = request("P001", "R001", 30);
        
        SysSemester pastSemester = new SysSemester();
        pastSemester.setSemesterName("2030-2031-1");
        pastSemester.setStartDate(LocalDate.of(2020, 9, 1));
        pastSemester.setTotalWeeks(20);
        when(semesterMapper.selectById("2030-2031-1")).thenReturn(pastSemester);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(req, "T001"));

        assertEquals("排课失败：排课时间不能早于当前时间！", e.getMessage());
    }

    @Test
    @DisplayName("教师同一时段已有排课")
    void rejectTeacherConflict() {
        mockBaseValidData();

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
        mockBaseValidData();

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
        mockBaseValidData();

        ExpSession exist = new ExpSession();
        exist.setWorkNo("T999");
        exist.setRoomNo("R999");
        exist.setProjectNo("P999");
        when(sessionMapper.selectList(any())).thenReturn(Collections.singletonList(exist));

        ExpProject other = new ExpProject();
        other.setProjectNo("P999");
        other.setClassNo("C001");
        
        // 业务代码已改为 selectBatchIds 批量查询，这里同步修改 Mock
        when(projectMapper.selectBatchIds(any())).thenReturn(Collections.singletonList(other));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addSession(request("P001", "R001", 30), "T001"));

        assertEquals("排课失败：该教学班在此时段已经有排课了！", e.getMessage());
    }

    @Test
    @DisplayName("排课成功：生成场次号、换算绝对时间、剩余座位等于容量")
    void addSessionSuccess() {
        mockBaseValidData();
        when(sessionMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(numberGenerator.generateSessionNo()).thenReturn("SE2030001");

        SessionCreateDTO req = request("P001", "R001", 30);
        String msg = service.addSession(req, "T001");

        assertEquals("场次安排成功！场次号为：SE2030001", msg);

        ArgumentCaptor<ExpSession> captor = ArgumentCaptor.forClass(ExpSession.class);
        verify(sessionMapper).insert(captor.capture());
        
        ExpSession saved = captor.getValue();
        assertEquals("T001", saved.getWorkNo());
        assertEquals("SE2030001", saved.getSessionNo());
        assertEquals(30, saved.getRemainingSeats());
        assertEquals(LocalDateTime.of(2030, 9, 16, 10, 0), saved.getSessionTime());
        
        // 验证释放了锁
        verify(lock).unlock();
    }

    @Test
    @DisplayName("学期配置缺失时拒绝排课")
    void rejectMissingSemester() {
        when(projectMapper.selectById("P001")).thenReturn(project("C001"));
        when(teachClassMapper.selectById("C001")).thenReturn(teachClass("T001"));
        when(labRoomMapper.selectById("R001")).thenReturn(room(50));
        when(semesterMapper.selectById("2030-2031-1")).thenReturn(null);

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

        assertEquals("非法操作：您无权取消该场次！",
                assertThrows(BusinessException.class,
                        () -> service.deleteSession("SE1", "T001")).getMessage());

        verify(sessionMapper, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("场次已有学生预约时拒绝取消排课")
    void rejectDeleteSessionWithBookings() {
        ExpSession session = new ExpSession();
        session.setSessionNo("SE1");
        session.setWorkNo("T001");
        when(sessionMapper.selectById("SE1")).thenReturn(session);
        when(bookingMapper.selectCount(any())).thenReturn(5L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteSession("SE1", "T001"));

        assertEquals("该场次已有 5 名学生预约，无法直接取消排课！", e.getMessage());
        verify(sessionMapper, never()).deleteById(anyString());
    }
}