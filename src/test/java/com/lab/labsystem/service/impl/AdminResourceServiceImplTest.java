package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.dto.request.LabCreateDTO;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.LabEquipmentMapper;
import com.lab.labsystem.mapper.LabRoomMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.SysSemesterMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.admin.impl.AdminResourceServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台资源维护的单元测试。
 *
 * <p>覆盖两类关注点：
 * <ol>
 *   <li>删除前的引用检查（实验室被场次/设备占用、课程被教学班使用、学期被引用）；</li>
 *   <li>列表查询的分页与上限保护（日志默认 100 条、其他资源不分页时走上限）。</li>
 * </ol>
 */
class AdminResourceServiceImplTest {

    private LabRoomMapper labRoomMapper;
    private LabEquipmentMapper equipMapper;
    private SysLogMapper logMapper;
    private ExpSessionMapper sessionMapper;
    private TeachClassMapper teachClassMapper;
    private NumberGenerator numberGenerator;
    private AdminResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        labRoomMapper = mock(LabRoomMapper.class);
        equipMapper = mock(LabEquipmentMapper.class);
        logMapper = mock(SysLogMapper.class);
        sessionMapper = mock(ExpSessionMapper.class);
        teachClassMapper = mock(TeachClassMapper.class);
        numberGenerator = mock(NumberGenerator.class);

        service = new AdminResourceServiceImpl(
                labRoomMapper,
                mock(ExpCourseMapper.class),
                equipMapper,
                mock(SysSemesterMapper.class),
                logMapper,
                sessionMapper,
                teachClassMapper,
                numberGenerator);

        // 默认无引用
        when(sessionMapper.selectCount(any())).thenReturn(0L);
        when(equipMapper.selectCount(any())).thenReturn(0L);
        when(teachClassMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    @DisplayName("日志不传分页参数时默认第 1 页 100 条")
    @SuppressWarnings("unchecked")
    void listLogsDefaultsToFirstPage() {
        when(logMapper.selectPage(any(), any())).thenReturn(new Page<>());

        service.listLogs(null, null);

        ArgumentCaptor<Page<SysLog>> captor = ArgumentCaptor.forClass(Page.class);
        verify(logMapper).selectPage(captor.capture(), any());
        assertEquals(1L, captor.getValue().getCurrent());
        assertEquals(100L, captor.getValue().getSize());
    }

    @Test
    @DisplayName("日志传了分页参数时按传入值查询")
    @SuppressWarnings("unchecked")
    void listLogsUsesGivenPage() {
        when(logMapper.selectPage(any(), any())).thenReturn(new Page<>());

        service.listLogs(3, 20);

        ArgumentCaptor<Page<SysLog>> captor = ArgumentCaptor.forClass(Page.class);
        verify(logMapper).selectPage(captor.capture(), any());
        assertEquals(3L, captor.getValue().getCurrent());
        assertEquals(20L, captor.getValue().getSize());
    }

    @Test
    @DisplayName("实验室不分页时仍受上限保护，不整表返回")
    @SuppressWarnings("unchecked")
    void listLabsUnpagedIsBounded() {
        Page<LabRoom> page = new Page<>(1, 200);
        LabRoom room = new LabRoom();
        room.setRoomNo("R001");
        page.setRecords(Collections.singletonList(room));
        doReturn(page).when(labRoomMapper).selectPage(any(), any());

        Object result = service.listLabs(null, null);

        assertInstanceOf(List.class, result);
        assertEquals(1, ((List<?>) result).size());
        verify(labRoomMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("新增实验室：生成编号、回填并落库")
    void addLab() {
        when(numberGenerator.generateRoomNo()).thenReturn("R001");
        
        LabCreateDTO dto = new LabCreateDTO();
        dto.setRoomName("测试实验室");

        String msg = service.addLab(dto);

        assertEquals("实验室添加成功！实验室号为：R001", msg);

        // 捕获传给 Mapper 的实体，验证主键是否被正确注入
        ArgumentCaptor<LabRoom> captor = ArgumentCaptor.forClass(LabRoom.class);
        verify(labRoomMapper).insert(captor.capture());
        
        LabRoom savedRoom = captor.getValue();
        assertEquals("R001", savedRoom.getRoomNo());
        assertEquals("测试实验室", savedRoom.getRoomName());
    }

    @Test
    @DisplayName("实验室仍被场次引用时拒绝删除")
    void rejectDeleteLabWithSessions() {
        when(sessionMapper.selectCount(any())).thenReturn(4L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteLab("R001"));

        assertEquals("该实验室下还有 4 个实验场次，请先处理后再删除！", e.getMessage());
        verify(labRoomMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("实验室仍被设备引用时拒绝删除")
    void rejectDeleteLabWithEquipments() {
        when(sessionMapper.selectCount(any())).thenReturn(0L);
        when(equipMapper.selectCount(any())).thenReturn(6L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteLab("R001"));

        assertEquals("该实验室下还有 6 台设备，请先处理后再删除！", e.getMessage());
        verify(labRoomMapper, never()).deleteById(any(String.class));
    }

    @Test
    @DisplayName("课程被教学班使用时拒绝删除")
    void rejectDeleteCourseInUse() {
        when(teachClassMapper.selectCount(any())).thenReturn(2L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteCourse("CO1"));

        assertEquals("该课程已被 2 个教学班使用，请先处理后再删除！", e.getMessage());
    }

    @Test
    @DisplayName("学期被教学班或场次引用时拒绝删除")
    void rejectDeleteSemesterInUse() {
        when(teachClassMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.deleteSemester("2025-2026-1"));

        assertEquals("该学期已被 1 个教学班使用，请先处理后再删除！", e.getMessage());
    }

    @Test
    @DisplayName("无引用时正常删除")
    void deleteWhenNoReferences() {
        service.deleteLab("R001");
        service.deleteCourse("CO1");
        service.deleteSemester("2025-2026-1");

        verify(labRoomMapper).deleteById("R001");
        verify(equipMapper, never()).deleteById(any(String.class));
    }
}