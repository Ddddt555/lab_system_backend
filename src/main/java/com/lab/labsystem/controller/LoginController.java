package com.lab.labsystem.controller;

import com.lab.labsystem.common.Result;
import com.lab.labsystem.dto.request.LoginDTO;
import com.lab.labsystem.dto.request.RefreshTokenDTO;
import com.lab.labsystem.service.auth.AuthFacade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "登录认证接口", description = "包含验证码获取、学生登录、教师登录、管理员登录、退出登录")
@RestController
@RequestMapping("/api")
public class LoginController {

    // 注入门面层
    private final AuthFacade authFacade;

    public LoginController(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<Object> getCaptcha() {
        return Result.success(authFacade.generateCaptcha());
    }

    @Operation(summary = "学生登录")
    @PostMapping("/login/student")
    public Result<Object> studentLogin(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.success(authFacade.loginStudent(dto, request));
    }

    @Operation(summary = "教师登录")
    @PostMapping("/login/teacher")
    public Result<Object> teacherLogin(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.success(authFacade.loginTeacher(dto, request));
    }

    @Operation(summary = "管理员登录")
    @PostMapping("/login/admin")
    public Result<Object> adminLogin(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.success(authFacade.loginAdmin(dto, request));
    }

    @Operation(summary = "【测试专用】免验证码登录")
    @PostMapping("/login/test")
    public Result<Object> testLogin(
            @RequestParam String username,
            @RequestParam String role,
            HttpServletRequest request) {
        // 测试接口保持原样，因为参数少
        return Result.success(authFacade.testLogin(username, role, request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        return Result.success(authFacade.logout(request));
    }

    @Operation(summary = "获取WebSocket连接票据")
    @GetMapping("/ws-ticket")
    public Result<String> getWsTicket(HttpServletRequest request) {
        String userNo = (String) request.getAttribute("currentUserNo");
        String role = (String) request.getAttribute("currentUserRole");
        return Result.success(authFacade.issueWsTicket(userNo, role));
    }

    @Operation(summary = "刷新 Access Token")
    @PostMapping("/refresh-token")
    public Result<Object> refreshToken(@Valid @RequestBody RefreshTokenDTO dto) {
        return Result.success(authFacade.refreshToken(dto));
    }
}