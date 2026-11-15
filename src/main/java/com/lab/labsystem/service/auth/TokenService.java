package com.lab.labsystem.service.auth;

import java.util.Map;

public interface TokenService {
    // 生成双 Token，并处理设备限制
    Map<String, Object> createTokenPair(String userNo, String role, String deviceId);

    // 刷新 Token，包含重放检测
    Map<String, Object> refreshToken(String refreshToken, String deviceId);

    // 注销当前设备的 Token
    void revokeTokens(String token);

    // 强制撤销该用户在全部设备上的会话（用于改密/重置密码）
    void revokeAllDevices(String userNo);

    // 签发 WS 票据
    String issueWsTicket(String userNo, String role);

    // 检查黑名单
    boolean isBlacklisted(String jti);
}