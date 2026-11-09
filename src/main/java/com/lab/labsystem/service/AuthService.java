package com.lab.labsystem.service;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 认证与会话。
 *
 * <p>从 {@code LoginController} 下沉出来的完整登录链路：验证码、三角色登录、
 * 失败次数锁定、双 Token 签发、退出黑名单、WebSocket 一次性票据、无感刷新。
 *
 * <p>保持与原先完全一致的提示文案与 Redis 键名/有效期。
 */
public interface AuthService {

    /** 生成图形验证码。 */
    Object generateCaptcha();

    /** 学生登录，返回 user / token / refreshToken。 */
    Map<String, Object> loginStudent(String username, String password,
                                     String captchaKey, String captchaCode,
                                     HttpServletRequest request);

    /** 教师登录。 */
    Map<String, Object> loginTeacher(String username, String password,
                                     String captchaKey, String captchaCode,
                                     HttpServletRequest request);

    /** 管理员登录。 */
    Map<String, Object> loginAdmin(String username, String password,
                                   String captchaKey, String captchaCode,
                                   HttpServletRequest request);

    /** 免验证码登录（仅非 prod 且显式开启时可用）。 */
    Map<String, Object> testLogin(String username, String role, HttpServletRequest request);

    /** 退出登录：当前 token 进黑名单，并清理 refresh token。 */
    String logout(HttpServletRequest request);

    /** 签发 WebSocket 连接票据（30 秒、一次性）。 */
    String issueWsTicket(String userNo, String role);

    /** 用 refresh token 换取新的 access token。 */
    Map<String, Object> refreshToken(String refreshToken);
}
