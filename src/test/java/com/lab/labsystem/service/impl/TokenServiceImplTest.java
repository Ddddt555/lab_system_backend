package com.lab.labsystem.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.service.auth.impl.TokenServiceImpl;

class TokenServiceImplTest {

    private static final String DEVICE_ID = "test-device";
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ZSetOperations<String, String> zSetOps;
    private TokenServiceImpl service;

    @BeforeAll
    static void initJwt() {
        JwtUtil util = new JwtUtil();
        util.setSecret("0123456789abcdef0123456789abcdef");
        util.setAccessExpireMinutes(30);
        util.setRefreshExpireDays(7);
        util.init();
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        zSetOps = mock(ZSetOperations.class);

        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForZSet()).thenReturn(zSetOps);

        service = new TokenServiceImpl(redis, mock(SysLogMapper.class));
    }

    @Test
    @DisplayName("生成 Token 对：写入 Redis 并处理设备列表")
    void createTokenPair() {
        Map<String, Object> tokens = service.createTokenPair("S001", "student", DEVICE_ID);

        assertNotNull(tokens.get("token"));
        assertNotNull(tokens.get("refreshToken"));

        verify(ops).set(eq("refresh_token:{S001:test-device}"), anyString(), any());
        verify(zSetOps).add(eq("user:devices:S001"), eq(DEVICE_ID), any(Double.class));
    }

    @Test
    @DisplayName("刷新令牌：轮换成功或命中有效缓存，返回新 Token")
    void refreshSuccessOrCached() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);

        // 模拟 Lua 脚本返回 SUCCESS 及拼接结果
        String luaResult = "SUCCESS|new-access-token|new-refresh-token|new-jti";
        when(redis.execute(
                ArgumentMatchers.<DefaultRedisScript<String>>any(),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(luaResult);

        Map<String, Object> data = service.refreshToken(refreshToken, DEVICE_ID);

        assertEquals("new-access-token", data.get("token"));
        assertEquals("new-refresh-token", data.get("refreshToken"));
        verify(zSetOps).add(eq("user:devices:S001"), eq(DEVICE_ID), any(Double.class));
    }

    @Test
    @DisplayName("刷新令牌：Lua 脚本返回 FAIL 时，判定为重放攻击并拦截")
    void refreshRejectsMismatch() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);

        when(redis.execute(
                ArgumentMatchers.<DefaultRedisScript<String>>any(),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn("FAIL");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refreshToken(refreshToken, DEVICE_ID));

        assertEquals("安全警告：检测到异常刷新操作，已被强制下线，请重新登录！", e.getMessage());
    }

    @Test
    @DisplayName("登出：应调用原子脚本清理主 Token 与幂等缓存，并移除设备")
    void revokeTokensClearsIdempotentCache() {
        String accessToken = JwtUtil.generateAccessToken("S001", "student", DEVICE_ID);
        String tokenWithBearer = "Bearer " + accessToken;

        service.revokeTokens(tokenWithBearer);

        verify(redis).execute(any(DefaultRedisScript.class), anyList());
        verify(zSetOps).remove("user:devices:S001", DEVICE_ID);
    }

    @Test
    @DisplayName("未登录不能获取 WS 票据")
    void wsTicketRequiresLogin() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.issueWsTicket(null, null));
        assertEquals("未登录，无法获取票据", e.getMessage());
    }
}