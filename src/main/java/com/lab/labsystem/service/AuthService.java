package com.lab.labsystem.service;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 认证与会话服务接口。
 *
 * <p>负责验证码校验、三角色登录、失败次数锁定、双 Token 签发、
 * 登出黑名单、WebSocket 一次性票据、无感刷新及设备绑定校验。
 */
public interface AuthService {

    /** 生成图形验证码。 */
    Object generateCaptcha();

    /** 学生登录，返回 user / token / refreshToken。 */
    Map<String, Object> loginStudent(String username, String password,
                                     String captchaKey, String captchaCode,
                                     String deviceId, HttpServletRequest request);

    /** 教师登录。 */
    Map<String, Object> loginTeacher(String username, String password,
                                     String captchaKey, String captchaCode,
                                     String deviceId, HttpServletRequest request);

    /** 管理员登录。 */
    Map<String, Object> loginAdmin(String username, String password,
                                   String captchaKey, String captchaCode,
                                   String deviceId, HttpServletRequest request);

    /** 免验证码登录（仅非 prod 且显式开启时可用）。 */
    Map<String, Object> testLogin(String username, String role, HttpServletRequest request);

    /** 退出登录：当前 Access Token 加入黑名单，并清理该设备对应的 Refresh Token。 */
    String logout(HttpServletRequest request);

    /** 签发 WebSocket 连接票据（30 秒有效、一次性）。 */
    String issueWsTicket(String userNo, String role);

    /** 使用 Refresh Token 换取新的 Access Token 及轮换后的 Refresh Token。 */
    Map<String, Object> refreshToken(String refreshToken, String deviceId);
}