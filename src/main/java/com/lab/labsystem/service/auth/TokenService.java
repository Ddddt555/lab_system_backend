package com.lab.labsystem.service.auth;

import java.util.Map;

public interface TokenService {
    // 生成双 Token，并处理设备限制
    Map<String, Object> createTokenPair(String userNo, String role, String deviceId);

    // 刷新 Token，包含重放检测
    Map<String, Object> refreshToken(String refreshToken, String deviceId);

    // 注销 Token
    void revokeTokens(String token);

    // 签发 WS 票据
    String issueWsTicket(String userNo, String role);

    // 检查黑名单
    boolean isBlacklisted(String jti);
}