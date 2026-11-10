package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BatchOperations;
import com.lab.labsystem.entity.SysNotice;
import com.lab.labsystem.entity.SysNoticeRead;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.SysNoticeMapper;
import com.lab.labsystem.mapper.SysNoticeReadMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知业务规则的单元测试。
 *
 * <p>这些规则原先写在 {@code NoticeController} 里，无法脱离 Spring 上下文测试。
 * 下沉到 service 后用 mock 直接覆盖，作为重构前后的行为契约。
 */
class NoticeServiceImplTest {

    private SysNoticeMapper noticeMapper;
    private SysNoticeReadMapper noticeReadMapper;
    private TeacherMapper teacherMapper;
    private BatchOperations batchOperations;
    private NoticeServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(SysNoticeMapper.class);
        noticeReadMapper = mock(SysNoticeReadMapper.class);
        teacherMapper = mock(TeacherMapper.class);
        batchOperations = mock(BatchOperations.class);
        service = new NoticeServiceImpl(noticeMapper, noticeReadMapper, teacherMapper, batchOperations);
    }

    private static SysNotice notice(String id) {
        SysNotice n = new SysNotice();
        n.setNoticeId(id);
        return n;
    }

    @Test
    @DisplayName("标题或内容为空时拒绝发布")
    void rejectBlankTitleOrContent() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> service.publish("  ", "内容", "all", "admin", "admin"));
        assertEquals("标题和内容不能为空！", e1.getMessage());

        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> service.publish("标题", "", "all", "admin", "admin"));
        assertEquals("标题和内容不能为空！", e2.getMessage());

        verify(noticeMapper, never()).insert(any(SysNotice.class));
    }

    @Test
    @DisplayName("目标角色不在白名单内时拒绝发布")
    void rejectIllegalTargetRole() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.publish("标题", "内容", "guest", "admin", "admin"));
        assertEquals("通知目标角色非法，仅支持 all/student/teacher！", e.getMessage());
        verify(noticeMapper, never()).insert(any(SysNotice.class));
    }

    @Test
    @DisplayName("教师只能向学生发布通知")
    void teacherCanOnlyPublishToStudent() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.publish("标题", "内容", "all", "teacher", "T001"));
        assertEquals("教师只能向学生发布通知！", e.getMessage());
        verify(noticeMapper, never()).insert(any(SysNotice.class));
    }

    @Test
    @DisplayName("管理员发布时发布人姓名固定为“管理员”")
    void adminPublishUsesFixedPublisherName() {
        assertEquals("通知发布成功！", service.publish("标题", "内容", "all", "admin", "admin"));

        ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
        verify(noticeMapper).insert(captor.capture());
        SysNotice saved = captor.getValue();
        assertEquals("管理员", saved.getPublisherName());
        assertEquals("all", saved.getTargetRole());
        assertTrue(saved.getNoticeId().startsWith("N"));
    }

    @Test
    @DisplayName("教师发布时从教师表取姓名")
    void teacherPublishUsesRealName() {
        Teacher teacher = new Teacher();
        teacher.setName("张老师");
        when(teacherMapper.selectById("T001")).thenReturn(teacher);

        service.publish("标题", "内容", "student", "teacher", "T001");

        ArgumentCaptor<SysNotice> captor = ArgumentCaptor.forClass(SysNotice.class);
        verify(noticeMapper).insert(captor.capture());
        assertEquals("张老师", captor.getValue().getPublisherName());
    }

    @Test
    @DisplayName("未读数量按已读记录计算")
    @SuppressWarnings("unchecked")
    void unreadCountExcludesReadNotices() {
        Page<SysNotice> noticePage = new Page<>(1, 200);
        noticePage.setRecords(Arrays.asList(notice("N1"), notice("N2"), notice("N3")));
        doReturn(noticePage).when(noticeMapper).selectPage(any(), any());

        SysNoticeRead read = new SysNoticeRead();
        read.setNoticeId("N1");
        Page<SysNoticeRead> readPage = new Page<>(1, 200);
        readPage.setRecords(Collections.singletonList(read));
        doReturn(readPage).when(noticeReadMapper).selectPage(any(), any());

        Map<String, Object> result = service.listForUser("student", "S001");

        assertEquals(3, ((java.util.List<?>) result.get("list")).size());
        assertEquals(2L, result.get("unreadCount"));
    }

    @Test
    @DisplayName("标记已读只插入未读的记录")
    @SuppressWarnings("unchecked")
    void markAllReadSkipsAlreadyRead() {
        Page<SysNotice> noticePage = new Page<>(1, 200);
        noticePage.setRecords(Arrays.asList(notice("N1"), notice("N2")));
        doReturn(noticePage).when(noticeMapper).selectPage(any(), any());

        SysNoticeRead read = new SysNoticeRead();
        read.setNoticeId("N1");
        Page<SysNoticeRead> readPage = new Page<>(1, 200);
        readPage.setRecords(Collections.singletonList(read));
        doReturn(readPage).when(noticeReadMapper).selectPage(any(), any());

        int count = service.markAllRead("student", "S001");

        assertEquals(1, count);

        // 只应把未读的那条（N2）交给批量插入，且不能退化为逐条 insert
        ArgumentCaptor<java.util.Collection<SysNoticeRead>> captor =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(batchOperations).saveBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("N2", captor.getValue().iterator().next().getNoticeId());
        verify(noticeReadMapper, never()).insert(any(SysNoticeRead.class));
    }

    @Test
    @DisplayName("列表接口对未知角色报错")
    void listRejectsUnknownRole() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.listForUser("guest", "X"));
        assertEquals("未知角色，无权查看通知！", e.getMessage());
    }
}
