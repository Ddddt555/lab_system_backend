package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 教师端项目管理规则的单元测试。
 *
 * <p>重点是两条越权防线：发布项目、上传指导书都必须校验教学班归属；
 * 以及指导书上传的格式/魔数校验必须在访问 MinIO 之前完成。
 */
class TeacherProjectServiceImplTest {

    private TeachClassMapper teachClassMapper;
    private ExpProjectMapper projectMapper;
    private ExpSessionMapper sessionMapper;
    private MinioService minioService;
    private NumberGenerator numberGenerator;
    private TeacherProjectServiceImpl service;

    @BeforeEach
    void setUp() {
        teachClassMapper = mock(TeachClassMapper.class);
        projectMapper = mock(ExpProjectMapper.class);
        sessionMapper = mock(ExpSessionMapper.class);
        minioService = mock(MinioService.class);
        numberGenerator = mock(NumberGenerator.class);

        service = new TeacherProjectServiceImpl(teachClassMapper, projectMapper, sessionMapper, minioService, numberGenerator);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "guide.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
    }

    @Test
    @DisplayName("没有教学班时项目列表为空")
    void listEmptyWhenNoClasses() {
        when(teachClassMapper.selectList(any())).thenReturn(Collections.emptyList());

        Object unpaged = service.listMyProjects("T001", null, null);
        assertInstanceOf(List.class, unpaged);
        assertTrue(((List<?>) unpaged).isEmpty());

        Object paged = service.listMyProjects("T001", 1, 10);
        assertInstanceOf(PageResult.class, paged);
        assertEquals(0L, ((PageResult<?>) paged).getTotal());
    }

    @Test
    @DisplayName("未选择教学班时拒绝发布项目")
    void addProjectRejectsMissingClass() {
        ExpProject project = new ExpProject();

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addProject(project, "T001"));

        assertEquals("请选择所属教学班！", e.getMessage());
        verify(projectMapper, never()).insert(any(ExpProject.class));
    }

    @Test
    @DisplayName("不能在其他教师的教学班下发布项目")
    void addProjectRejectsOtherTeacherClass() {
        ExpProject project = new ExpProject();
        project.setClassNo("C001");

        TeachClass tc = new TeachClass();
        tc.setClassNo("C001");
        tc.setWorkNo("T999");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.addProject(project, "T001"));

        assertEquals("非法操作：您无权在该教学班下发布项目！", e.getMessage());
        verify(projectMapper, never()).insert(any(ExpProject.class));
    }

    @Test
    @DisplayName("发布成功时生成项目号并落库")
    void addProjectSuccess() {
        ExpProject project = new ExpProject();
        project.setClassNo("C001");

        TeachClass tc = new TeachClass();
        tc.setClassNo("C001");
        tc.setWorkNo("T001");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);
        when(numberGenerator.generateProjectNo()).thenReturn("P2025001");

        String msg = service.addProject(project, "T001");

        assertTrue(msg.contains("P2025001"));
        ArgumentCaptor<ExpProject> captor = ArgumentCaptor.forClass(ExpProject.class);
        verify(projectMapper).insert(captor.capture());
        assertEquals("P2025001", captor.getValue().getProjectNo());
    }

    @Test
    @DisplayName("空文件被拒绝")
    void uploadGuideRejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.uploadGuide(empty, "P001", "T001"));

        assertEquals("文件不能为空！", e.getMessage());
    }

    @Test
    @DisplayName("项目不存在时拒绝上传")
    void uploadGuideRejectsMissingProject() {
        when(projectMapper.selectById("P404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.uploadGuide(pdf(), "P404", "T001"));

        assertEquals("项目不存在！", e.getMessage());
    }

    @Test
    @DisplayName("不能操作其他教师的项目")
    void uploadGuideRejectsOtherTeacher() throws Exception {
        ExpProject project = new ExpProject();
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T999");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.uploadGuide(pdf(), "P001", "T001"));

        assertEquals("非法操作：您无权操作该项目！", e.getMessage());
        verify(minioService, never()).upload(any(), anyString());
    }

    @Test
    @DisplayName("非法后缀在访问 MinIO 之前就被拦截")
    void uploadGuideRejectsIllegalExtension() throws Exception {
        ExpProject project = new ExpProject();
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T001");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        MockMultipartFile exe = new MockMultipartFile("file", "evil.exe", "application/octet-stream", "MZ..".getBytes());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.uploadGuide(exe, "P001", "T001"));

        assertEquals("文件格式非法！", e.getMessage());
        verify(minioService, never()).upload(any(), anyString());
    }

    // ================= 新增的删除项目测试 =================

    @Test
    @DisplayName("项目不存在时拒绝删除")
    void deleteProjectRejectsMissingProject() {
        when(projectMapper.selectById("P404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteProject("P404", "T001"));

        assertEquals("实验项目不存在！", e.getMessage());
        verify(projectMapper, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("不能删除其他教师的项目")
    void deleteProjectRejectsOtherTeacher() {
        ExpProject project = new ExpProject();
        project.setProjectNo("P001");
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T999");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteProject("P001", "T001"));

        assertEquals("非法操作：您无权删除该项目！", e.getMessage());
        verify(projectMapper, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("项目下存在排课时拒绝删除")
    void deleteProjectRejectsWhenHasSessions() {
        ExpProject project = new ExpProject();
        project.setProjectNo("P001");
        project.setClassNo("C001");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T001");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        // 模拟该场次下还有 3 个排课
        when(sessionMapper.selectCount(any())).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteProject("P001", "T001"));

        assertEquals("该项目下还有 3 个实验场次，请先取消排课后再删除！", e.getMessage());
        verify(projectMapper, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("删除成功并清理指导书文件")
    void deleteProjectSuccess() {
        ExpProject project = new ExpProject();
        project.setProjectNo("P001");
        project.setClassNo("C001");
        project.setGuideFile("test-guide.pdf");
        when(projectMapper.selectById("P001")).thenReturn(project);

        TeachClass tc = new TeachClass();
        tc.setWorkNo("T001");
        when(teachClassMapper.selectById("C001")).thenReturn(tc);

        when(sessionMapper.selectCount(any())).thenReturn(0L);

        String msg = service.deleteProject("P001", "T001");

        assertEquals("实验项目删除成功！", msg);
        verify(projectMapper).deleteById("P001");
        verify(minioService).remove("test-guide.pdf");
    }
}