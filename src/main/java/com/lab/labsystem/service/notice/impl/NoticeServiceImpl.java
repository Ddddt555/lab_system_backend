package com.lab.labsystem.service.notice.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.common.BatchOperations;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.websocket.GlobalWebSocket;
import com.lab.labsystem.entity.SysNotice;
import com.lab.labsystem.entity.SysNoticeRead;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.SysNoticeMapper;
import com.lab.labsystem.mapper.SysNoticeReadMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.notice.NoticeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NoticeServiceImpl implements NoticeService {

    // 允许的 target_role 白名单
    private static final Set<String> ALLOWED_TARGET_ROLES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("all", "student", "teacher")));

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeReadMapper noticeReadMapper;
    private final TeacherMapper teacherMapper;
    private final BatchOperations batchOperations;

    public NoticeServiceImpl(SysNoticeMapper noticeMapper,
                             SysNoticeReadMapper noticeReadMapper,
                             TeacherMapper teacherMapper,
                             BatchOperations batchOperations) {
        this.noticeMapper = noticeMapper;
        this.noticeReadMapper = noticeReadMapper;
        this.teacherMapper = teacherMapper;
        this.batchOperations = batchOperations;
    }

    @Override
    public String publish(String title, String content, String targetRole, String role, String userNo) {
        if (title == null || title.trim().isEmpty()
                || content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("标题和内容不能为空！");
        }

        if (targetRole == null || !ALLOWED_TARGET_ROLES.contains(targetRole)) {
            throw new IllegalArgumentException("通知目标角色非法，仅支持 all/student/teacher！");
        }

        if ("teacher".equals(role) && !"student".equals(targetRole)) {
            throw new IllegalArgumentException("教师只能向学生发布通知！");
        }

        String publisherName;
        if ("admin".equals(role)) {
            publisherName = "管理员";
        } else {
            Teacher teacher = teacherMapper.selectById(userNo);
            publisherName = teacher != null ? teacher.getName() : "教师";
        }

        SysNotice notice = new SysNotice();
        notice.setNoticeId("N" + UUID.randomUUID().toString().substring(0, 8));
        notice.setTitle(title);
        notice.setContent(content);
        notice.setPublisherName(publisherName);
        notice.setTargetRole(targetRole);
        notice.setCreateTime(LocalDateTime.now());
        noticeMapper.insert(notice);

        // 通知入库成功后广播 NEW_NOTICE
        GlobalWebSocket.broadcastToRole(targetRole, "{\"type\":\"NEW_NOTICE\"}");

        return "通知发布成功！";
    }

    @Override
    public Map<String, Object> listForUser(String role, String userNo) {
        if (role == null || userNo == null) {
            throw new IllegalArgumentException("未登录或身份失效！");
        }

        List<SysNotice> notices = QueryLimits.list(noticeMapper, visibleNoticesQuery(role, true));
        Set<String> readIds = readIdsOf(userNo);

        long unreadCount = notices.stream()
                .filter(n -> !readIds.contains(n.getNoticeId()))
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("list", notices);
        result.put("unreadCount", unreadCount);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(String role, String userNo) {
        if (userNo == null) {
            throw new IllegalArgumentException("未登录");
        }

        List<SysNotice> notices = QueryLimits.list(noticeMapper, visibleNoticesQuery(role, false));
        Set<String> readIds = readIdsOf(userNo);

        // 解决写放大：收集需要插入的记录，最后批量提交
        List<SysNoticeRead> readList = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (SysNotice notice : notices) {
            if (!readIds.contains(notice.getNoticeId())) {
                SysNoticeRead read = new SysNoticeRead();
                read.setNoticeId(notice.getNoticeId());
                read.setUserNo(userNo);
                read.setReadTime(now);
                readList.add(read);
            }
        }
        
        if (!readList.isEmpty()) {
            // 批量插入（通过适配层，便于单元测试验证）
            batchOperations.saveBatch(readList);
        }
        
        return readList.size();
    }

    /**
     * 构造当前角色可见的通知查询条件。
     * 学生/教师可见 all + 自己角色；管理员只可见 all。
     */
    private QueryWrapper<SysNotice> visibleNoticesQuery(String role, boolean rejectUnknownRole) {
        QueryWrapper<SysNotice> qw = new QueryWrapper<>();
        if ("student".equals(role) || "teacher".equals(role)) {
            qw.and(w -> w.eq("target_role", "all").or().eq("target_role", role));
        } else if (rejectUnknownRole && !"admin".equals(role)) {
            throw new IllegalArgumentException("未知角色，无权查看通知！");
        } else {
            qw.eq("target_role", "all");
        }
        qw.orderByDesc("create_time");
        return qw;
    }

    private Set<String> readIdsOf(String userNo) {
        List<SysNoticeRead> reads = QueryLimits.list(noticeReadMapper,
                new QueryWrapper<SysNoticeRead>().eq("user_no", userNo));
        return reads.stream()
                .map(SysNoticeRead::getNoticeId)
                .collect(Collectors.toSet());
    }
}