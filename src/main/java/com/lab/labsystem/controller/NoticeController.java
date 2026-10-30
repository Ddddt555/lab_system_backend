package com.lab.labsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.annotation.RequireRole; // ⚠️ 新增导入
import com.lab.labsystem.common.Result;
import com.lab.labsystem.entity.SysNotice;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.SysNoticeMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Tag(name = "通知管理接口", description = "包含教师/管理员发布通知、三端获取通知列表")
@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    @Autowired private SysNoticeMapper noticeMapper;
    @Autowired private TeacherMapper teacherMapper;

    /** 允许的 target_role 白名单 */
    private static final Set<String> ALLOWED_TARGET_ROLES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList("all", "student", "teacher")));

    @Operation(summary = "发布通知", description = "仅限教师和管理员调用。发布人姓名由后端从 JWT 中提取，不可伪造；教师只能发学生通知")
    @RequireRole({"teacher", "admin"}) // ⚠️ 新增：只有教师和管理员可以访问该接口
    @PostMapping("/publish")
    public Result<String> publishNotice(@RequestParam String title,
                                        @RequestParam String content,
                                        @RequestParam String targetRole,
                                        HttpServletRequest request) {

        String role = (String) request.getAttribute("currentUserRole");
        String userNo = (String) request.getAttribute("currentUserNo");

        // ⚠️ 注：原来手写的 if (!"teacher".equals(role) && !"admin".equals(role)) 鉴权代码已移除，由 @RequireRole 统一接管。

        if (title == null || title.trim().isEmpty()
                || content == null || content.trim().isEmpty()) {
            return Result.error("标题和内容不能为空！");
        }

        // 1. targetRole 白名单校验，禁止前端传入任意字符串
        if (targetRole == null || !ALLOWED_TARGET_ROLES.contains(targetRole)) {
            return Result.error("通知目标角色非法，仅支持 all/student/teacher！");
        }

        // 2. 教师不允许发 all/teacher，只能发给学生
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

    @Operation(summary = "获取通知列表", description = "角色由 JWT 决定，前端不再传 role。student/teacher 返回 all + 本角色；admin 返回 all")
    @GetMapping("/list")
    public Result<List<SysNotice>> getNoticeList(HttpServletRequest request) {

        // 1. 从 JWT 解析出的角色，不再信任前端参数
        String role = (String) request.getAttribute("currentUserRole");
        if (role == null) {
            return Result.error("未登录或身份失效！");
        }

        QueryWrapper<SysNotice> qw = new QueryWrapper<>();
        switch (role) {
            case "student":
            case "teacher":
                // 公共通知 + 本角色定向通知
                qw.and(w -> w.eq("target_role", "all").or().eq("target_role", role));
                break;
            case "admin":
                // 管理员当前只接收 all；将来若支持 admin 定向，在这里追加 .eq("target_role","admin")
                qw.eq("target_role", "all");
                break;
            default:
                return Result.error("未知角色，无权查看通知！");
        }
        qw.orderByDesc("create_time");
        return Result.success(noticeMapper.selectList(qw));
    }
}