package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.LabEquipmentMapper;
import com.lab.labsystem.mapper.LabRoomMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.SysSemesterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台资源维护的单元测试。
 *
 * <p>重点是两条与安全/稳定性相关的行为：
 * <ol>
 *   <li>操作日志不传分页参数时默认第 1 页 100 条，绝不整表返回；</li>
 *   <li>实验室/课程/学期在没有分页参数时走 QueryLimits 上限，而不是 selectList 全量。</li>
 * </ol>
 */
class AdminResourceServiceImplTest {

    private LabRoomMapper labRoomMapper;
    private SysLogMapper logMapper;
    private NumberGenerator numberGenerator;
    private AdminResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        labRoomMapper = mock(LabRoomMapper.class);
        logMapper = mock(SysLogMapper.class);
        numberGenerator = mock(NumberGenerator.class);

        service = new AdminResourceServiceImpl(
                labRoomMapper,
                mock(ExpCourseMapper.class),
                mock(LabEquipmentMapper.class),
                mock(SysSemesterMapper.class),
                logMapper,
                numberGenerator);
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
        // 走的是 selectPage（带上限），而不是 selectList
        verify(labRoomMapper).selectPage(any(), any());
    }

    @Test
    @DisplayName("新增实验室：生成编号、回填并落库")
    void addLab() {
        when(numberGenerator.generateRoomNo()).thenReturn("R001");
        LabRoom room = new LabRoom();

        String msg = service.addLab(room);

        assertEquals("R001", room.getRoomNo());
        verify(labRoomMapper).insert(room);
        assertEquals("实验室添加成功！实验室号为：R001", msg);
    }

    @Test
    @DisplayName("删除直接委托 Mapper")
    void deleteLab() {
        service.deleteLab("R001");
        verify(labRoomMapper).deleteById("R001");
    }
}
