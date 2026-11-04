package com.lab.labsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.entity.SysNotice;
import com.lab.labsystem.entity.SysNoticeRead;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.SysNoticeMapper;
import com.lab.labsystem.mapper.SysNoticeReadMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "通知管理接口", description = "包含教师/管理员发布通知、三端获取通知列表")
@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    @Autowired private SysNoticeMapper noticeMapper;
    @Autowired private SysNoticeReadMapper noticeReadMapper; // 🛑【核心新增】注入已读记录 Mapper
    @Autowired private TeacherMapper teacherMapper;

    /** 允许的 target_role 白名单 */
    private static final Set<String> ALLOWED_TARGET_ROLES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("all", "student", "teacher")));

    @Operation(summary = "发布通知", description = "仅限教师和管理员调用。发布人姓名由后端从 JWT 中提取，不可伪造；教师只能发学生通知")
    @RequireRole({"teacher", "admin"})
    @PostMapping("/publish")
    public Result<String> publishNotice(@RequestParam String title,
                                        @RequestParam String content,
                                        @RequestParam String targetRole,
                                        HttpServletRequest request) {

        String role = (String) request.getAttribute("currentUserRole");
        String userNo = (String) request.getAttribute("currentUserNo");

        if (title == null || title.trim().isEmpty()
                || content == null || content.trim().isEmpty()) {
            return Result.error("标题和内容不能为空！");
        }

        if (targetRole == null || !ALLOWED_TARGET_ROLES.contains(targetRole)) {
            return Result.error("通知目标角色非法，仅支持 all/student/teacher！");
        }

        if ("teacher".equals(role) && !"student".equals(targetRole)) {
            return Result.error("教师只能向学生发布通知！");
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

        return Result.success("通知发布成功！");
    }

    @Operation(summary = "获取通知列表", description = "返回通知列表和未读数量，角色由 JWT 决定")
    @GetMapping("/list")
    public Result<Object> getNoticeList(HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        String userNo = (String) request.getAttribute("currentUserNo");
        
        if (role == null || userNo == null) {
            return Result.error("未登录或身份失效！");
        }

        QueryWrapper<SysNotice> qw = new QueryWrapper<>();
        switch (role) {
            case "student":
            case "teacher":
                qw.and(w -> w.eq("target_role", "all").or().eq("target_role", role));
                break;
            case "admin":
                qw.eq("target_role", "all");
                break;
            default:
                return Result.error("未知角色，无权查看通知！");
        }
        qw.orderByDesc("create_time");
        List<SysNotice> notices = noticeMapper.selectList(qw);

        // 🛑【核心新增】查询当前用户已读的通知ID集合
        List<SysNoticeRead> reads = noticeReadMapper.selectList(
                new QueryWrapper<SysNoticeRead>().eq("user_no", userNo));
        Set<String> readIds = reads.stream()
                .map(SysNoticeRead::getNoticeId)
                .collect(Collectors.toSet());

        // 组装返回数据
        Map<String, Object> result = new HashMap<>();
        result.put("list", notices);
        
        // 计算未读数
        long unreadCount = notices.stream().filter(n -> !readIds.contains(n.getNoticeId())).count();
        result.put("unreadCount", unreadCount);

        return Result.success(result);
    }

    // 🛑【核心新增】标记通知已读
    @Operation(summary = "标记通知已读")
    @PostMapping("/mark-read")
    public Result<String> markRead(HttpServletRequest request) {
        String userNo = (String) request.getAttribute("currentUserNo");
        String role = (String) request.getAttribute("currentUserRole");
        if (userNo == null) return Result.error("未登录");

        // 查出当前用户能看到的所有通知
        QueryWrapper<SysNotice> qw = new QueryWrapper<>();
        if ("student".equals(role) || "teacher".equals(role)) {
            qw.and(w -> w.eq("target_role", "all").or().eq("target_role", role));
        } else {
            qw.eq("target_role", "all");
        }
        List<SysNotice> notices = noticeMapper.selectList(qw);

        // 查出已读记录，避免重复插入
        List<SysNoticeRead> reads = noticeReadMapper.selectList(
                new QueryWrapper<SysNoticeRead>().eq("user_no", userNo));
        Set<String> readIds = reads.stream()
                .map(SysNoticeRead::getNoticeId)
                .collect(Collectors.toSet());

        // 插入未读的通知记录
        int count = 0;
        for (SysNotice notice : notices) {
            if (!readIds.contains(notice.getNoticeId())) {
                SysNoticeRead read = new SysNoticeRead();
                read.setNoticeId(notice.getNoticeId());
                read.setUserNo(userNo);
                read.setReadTime(LocalDateTime.now());
                noticeReadMapper.insert(read);
                count++;
            }
        }
        return Result.success("已标记 " + count + " 条通知为已读");
    }
}