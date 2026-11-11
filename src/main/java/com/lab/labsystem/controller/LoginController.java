package com.lab.labsystem.controller;

import com.lab.labsystem.common.Result;
import com.lab.labsystem.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 登录认证接口。
 *
 * <p>本类只做参数接收与响应包装，认证逻辑（验证码、锁定策略、双 Token、
 * 黑名单、WS 票据、刷新）全部在 {@link AuthService}。
 */
@Tag(name = "登录认证接口", description = "包含验证码获取、学生登录、教师登录、管理员登录、退出登录")
@RestController
@RequestMapping("/api")
public class LoginController {

    private final AuthService authService;

    public LoginController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<Object> getCaptcha() {
        return Result.success(authService.generateCaptcha());
    }

    @Operation(summary = "学生登录")
    @PostMapping("/login/student")
    public Result<Object> studentLogin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String captchaKey,
            @RequestParam String captchaCode,
            @RequestParam(required = false, defaultValue = "web-default") String deviceId,
            HttpServletRequest request) {
        return Result.success(authService.loginStudent(username, password, captchaKey, captchaCode, deviceId, request));
    }

    @Operation(summary = "教师登录")
    @PostMapping("/login/teacher")
    public Result<Object> teacherLogin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String captchaKey,
            @RequestParam String captchaCode,
            @RequestParam(required = false, defaultValue = "web-default") String deviceId,
            HttpServletRequest request) {
        return Result.success(authService.loginTeacher(username, password, captchaKey, captchaCode, deviceId, request));
    }

    @Operation(summary = "管理员登录")
    @PostMapping("/login/admin")
    public Result<Object> adminLogin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String captchaKey,
            @RequestParam String captchaCode,
            @RequestParam(required = false, defaultValue = "web-default") String deviceId,
            HttpServletRequest request) {
        return Result.success(authService.loginAdmin(username, password, captchaKey, captchaCode, deviceId, request));
    }

    @Operation(summary = "【测试专用】免验证码登录")
    @PostMapping("/login/test")
    public Result<Object> testLogin(
            @RequestParam String username,
            @RequestParam String role,
            HttpServletRequest request) {
        return Result.success(authService.testLogin(username, role, request));
    }

    @Operation(summary = "退出登录", description = "将当前 Token 加入 Redis 黑名单，使其立即失效")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        return Result.success(authService.logout(request));
    }

    @Operation(summary = "获取WebSocket连接票据", description = "用于替代URL传JWT，有效期30秒")
    @GetMapping("/ws-ticket")
    public Result<String> getWsTicket(HttpServletRequest request) {
        String userNo = (String) request.getAttribute("currentUserNo");
        String role = (String) request.getAttribute("currentUserRole");
        return Result.success(authService.issueWsTicket(userNo, role));
    }

    @Operation(summary = "刷新 Access Token", description = "使用 Refresh Token 换取新的 Access Token")
    @PostMapping("/refresh-token")
    public Result<Object> refreshToken(
            @RequestParam(required = false) String refreshToken,
            @RequestBody(required = false) Map<String, String> body) {
        // 优先从请求体读取，避免 Refresh Token 出现在 URL 查询串中。
        // 保留 query 参数作为兼容入口。
        String token = refreshToken;
        String deviceId = null;
        if (body != null) {
            if (token == null) {
                token = body.get("refreshToken");
            }
            deviceId = body.get("deviceId");
        }
        return Result.success(authService.refreshToken(token, deviceId));
    }
}