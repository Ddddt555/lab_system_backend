package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpReport;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.AdminClassMapper;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpReportMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.file.MinioService;
import com.lab.labsystem.service.report.impl.ReportServiceImpl;
import com.lab.labsystem.service.teacher.TeacherScoreService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实验报告业务的单元测试。
 *
 * <p>重点覆盖两类风险：
 * <ol>
 *   <li>参数/权限校验失败时的提示文案与顺序（重构后必须与 controller 版本一致）；</li>
 *   <li>上传失败路径不会误删 MinIO 文件 —— 业务拒绝（如没抢到锁）
 *       必须原样抛出，不能被通用 catch 包装成"数据库失败"并清理文件。</li>
 * </ol>
 */
class ReportServiceImplTest {

    private ExpReportMapper reportMapper;
    private ExpProjectMapper projectMapper;
    private TeachClassMapper teachClassMapper;
    private MinioService minioService;
    private TeacherScoreService teacherScoreService;
    private RedissonClient redissonClient;
    private RLock lock;
    private ReportServiceImpl service;

    @BeforeEach
    void setUp() {
        reportMapper = mock(ExpReportMapper.class);
        projectMapper = mock(ExpProjectMapper.class);
        teachClassMapper = mock(TeachClassMapper.class);
        minioService = mock(MinioService.class);
        teacherScoreService = mock(TeacherScoreService.class);
        redissonClient = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redissonClient.getLock(anyString())).thenReturn(lock);

        service = new ReportServiceImpl(
                reportMapper,
                mock(StudentMapper.class),
                mock(AdminClassMapper.class),
                mock(MajorMapper.class),
                mock(DepartmentMapper.class),
                projectMapper,
                teachClassMapper,
                minioService,
                teacherScoreService,
                mock(StringRedisTemplate.class),
                redissonClient);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf",
                "%PDF-1.4 test".getBytes());
    }

    @Test
    @DisplayName("空文件被拒绝")
    void rejectEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.upload(empty, "P001", "S001"));
        assertEquals("上传的文件不能为空！", e.getMessage());
    }

    @Test
    @DisplayName("非法扩展名被拒绝")
    void rejectIllegalExtension() {
        MockMultipartFile exe = new MockMultipartFile("file", "a.exe", "application/octet-stream",
                "MZ....".getBytes());
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.upload(exe, "P001", "S001"));
        assertEquals("文件格式非法！只允许上传 PDF、Word、ZIP、RAR 格式。", e.getMessage());
    }

    @Test
    @DisplayName("项目不存在时拒绝上传")
    void rejectMissingProject() throws Exception {
        when(projectMapper.selectById("P404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.upload(pdf(), "P404", "S001"));
        assertEquals("实验项目不存在！", e.getMessage());
        verify(minioService, never()).upload(any(), anyString());
    }

    @Test
    @DisplayName("没抢到上传锁时应原样拒绝，且不误删 MinIO 文件")
    void lockBusyDoesNotCleanUpFile() throws Exception {
        when(projectMapper.selectById("P001")).thenReturn(new ExpProject());
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.upload(pdf(), "P001", "S001"));

        assertEquals("您有报告正在上传中，请勿频繁点击！", e.getMessage());
        // 关键回归点：锁冲突时既没有上传文件，也不能调用 remove
        verify(minioService, never()).upload(any(), anyString());
        verify(minioService, never()).remove(anyString());
    }

    @Test
    @DisplayName("分数越界被拒绝")
    void gradeRejectsOutOfRangeScore() {
        BusinessException e1 = assertThrows(BusinessException.class,
                () -> service.grade("R1", -1d, null, "T001"));
        assertEquals("分数必须在 0-100 之间！", e1.getMessage());

        BusinessException e2 = assertThrows(BusinessException.class,
                () -> service.grade("R1", 101d, null, "T001"));
        assertEquals("分数必须在 0-100 之间！", e2.getMessage());

        verify(reportMapper, never()).updateById(any(ExpReport.class));
    }

    @Test
    @DisplayName("报告不存在时拒绝批阅")
    void gradeRejectsUnknownReport() {
        when(reportMapper.selectById("R404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.grade("R404", 90d, "不错", "T001"));
        assertEquals("找不到该报告！", e.getMessage());
    }

    @Test
    @DisplayName("非授课教师不能批阅")
    void gradeRejectsOtherTeacher() {
        ExpReport report = new ExpReport();
        report.setReportNo("R1");
        report.setProjectNo("P001");
        when(reportMapper.selectById("R1")).thenReturn(report);

        ExpProject project = new ExpProject();
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T999");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.grade("R1", 90d, "不错", "T001"));
        assertEquals("非法操作：您无权批阅该报告！", e.getMessage());
        verify(reportMapper, never()).updateById(any(ExpReport.class));
    }

    @Test
    @DisplayName("批阅成功后触发成绩重算")
    void gradeUpdatesAndRecalculates() {
        ExpReport report = new ExpReport();
        report.setReportNo("R1");
        report.setProjectNo("P001");
        report.setStudentNo("S001");
        when(reportMapper.selectById("R1")).thenReturn(report);

        ExpProject project = new ExpProject();
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T001");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        service.grade("R1", 88d, "写得好", "T001");

        assertEquals(88d, report.getScore());
        assertEquals("写得好", report.getComment());
        verify(reportMapper).updateById(report);
        verify(teacherScoreService).recalculateAfterGrade("S001", "C001");
    }

    @Test
    @DisplayName("项目不存在时拒绝查看报告列表")
    void listByProjectRejectsUnknownProject() {
        when(projectMapper.selectById("P404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.listByProject("P404", "T001"));
        assertEquals("实验项目不存在！", e.getMessage());
    }

    @Test
    @DisplayName("非授课教师不能查看项目报告列表")
    void listByProjectRejectsOtherTeacher() {
        ExpProject project = new ExpProject();
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T999");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.listByProject("P001", "T001"));
        assertEquals("非法操作：您无权查看该项目报告！", e.getMessage());
    }

    @Test
    @DisplayName("项目下没有报告时返回空列表")
    @SuppressWarnings("unchecked")
    void listByProjectReturnsEmptyList() {
        ExpProject project = new ExpProject();
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T001");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        Page<ExpReport> emptyPage = new Page<>(1, 200);
        emptyPage.setRecords(Collections.emptyList());
        doReturn(emptyPage).when(reportMapper).selectPage(any(), any());

        assertEquals(0, service.listByProject("P001", "T001").size());
    }
}
