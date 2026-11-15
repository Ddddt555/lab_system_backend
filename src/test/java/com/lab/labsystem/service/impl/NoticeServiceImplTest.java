package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.entity.SysNotice;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.SysNoticeMapper;
import com.lab.labsystem.mapper.SysNoticeReadMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.notice.impl.NoticeServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知业务规则的单元测试。
 */
class NoticeServiceImplTest {

    private SysNoticeMapper noticeMapper;
    private SysNoticeReadMapper noticeReadMapper;
    private TeacherMapper teacherMapper;
    private NoticeServiceImpl service;

    @BeforeEach
    void setUp() {
        noticeMapper = mock(SysNoticeMapper.class);
        noticeReadMapper = mock(SysNoticeReadMapper.class);
        teacherMapper = mock(TeacherMapper.class);
        service = new NoticeServiceImpl(noticeMapper, noticeReadMapper, teacherMapper);
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
    @DisplayName("未读数量直接查库统计")
    @SuppressWarnings("unchecked")
    void unreadCountUsesDatabaseCount() {
        Page<SysNotice> noticePage = new Page<>(1, 200);
        noticePage.setRecords(Arrays.asList(notice("N1"), notice("N2"), notice("N3")));
        doReturn(noticePage).when(noticeMapper).selectPage(any(), any());

        when(noticeMapper.countUnread("S001", "student")).thenReturn(250L);

        Map<String, Object> result = service.listForUser("student", "S001");

        assertEquals(3, ((java.util.List<?>) result.get("list")).size());
        assertEquals(250L, result.get("unreadCount"));
    }

    @Test
    @DisplayName("全部已读直接执行聚合 SQL")
    void markAllReadUsesDatabaseInsert() {
        when(noticeReadMapper.markAllRead("S001", "student")).thenReturn(15);

        int count = service.markAllRead("student", "S001");

        assertEquals(15, count);
        verify(noticeReadMapper).markAllRead("S001", "student");
    }

    @Test
    @DisplayName("列表接口对未知角色报错")
    void listRejectsUnknownRole() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.listForUser("guest", "X"));
        assertEquals("未知角色，无权查看通知！", e.getMessage());
    }
}