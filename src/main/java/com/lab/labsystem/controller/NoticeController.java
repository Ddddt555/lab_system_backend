package com.lab.labsystem.controller;

import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知管理接口。
 *
 * <p>本类只负责 HTTP 层：取 JWT 中的身份、调用 service、包装响应。
 * 业务规则（发布校验、可见范围、未读统计）都在 {@link NoticeService} 中。
 */
@Tag(name = "通知管理接口", description = "包含教师/管理员发布通知、三端获取通知列表")
@RestController
@RequestMapping("/api/notice")
public class NoticeController {

    private final NoticeService noticeService;

    public NoticeController(NoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @Operation(summary = "发布通知", description = "仅限教师和管理员调用。发布人姓名由后端从 JWT 中提取，不可伪造；教师只能发学生通知")
    @RequireRole({"teacher", "admin"})
    @PostMapping("/publish")
    public Result<String> publishNotice(@RequestParam String title,
                                        @RequestParam String content,
                                        @RequestParam String targetRole,
                                        HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        String userNo = (String) request.getAttribute("currentUserNo");
        return Result.success(noticeService.publish(title, content, targetRole, role, userNo));
    }

    @Operation(summary = "获取通知列表", description = "返回通知列表和未读数量，角色由 JWT 决定")
    @GetMapping("/list")
    public Result<Object> getNoticeList(HttpServletRequest request) {
        String role = (String) request.getAttribute("currentUserRole");
        String userNo = (String) request.getAttribute("currentUserNo");
        return Result.success(noticeService.listForUser(role, userNo));
    }

    @Operation(summary = "标记通知已读")
    @PostMapping("/mark-read")
    public Result<String> markRead(HttpServletRequest request) {
        String userNo = (String) request.getAttribute("currentUserNo");
        String role = (String) request.getAttribute("currentUserRole");
        int count = noticeService.markAllRead(role, userNo);
        return Result.success("已标记 " + count + " 条通知为已读");
    }
}
