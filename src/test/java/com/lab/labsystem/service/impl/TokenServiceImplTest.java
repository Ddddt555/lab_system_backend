package com.lab.labsystem.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.service.auth.impl.TokenServiceImpl;

class TokenServiceImplTest {

    private static final String DEVICE_ID = "test-device";
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ZSetOperations<String, String> zSetOps;
    private SysLogMapper logMapper;
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
        logMapper = mock(SysLogMapper.class);

        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForZSet()).thenReturn(zSetOps);

        service = new TokenServiceImpl(redis, logMapper);
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
    @DisplayName("刷新令牌：轮换成功，返回新 Token 并更新活跃时间")
    void refreshSuccess() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);

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
    @DisplayName("刷新令牌：命中并发幂等缓存，返回缓存中的 Token")
    void refreshCached() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);

        String luaResult = "CACHED|cached-access-token|cached-refresh-token|cached-jti";
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

        assertEquals("cached-access-token", data.get("token"));
        assertEquals("cached-refresh-token", data.get("refreshToken"));
        verify(zSetOps).add(eq("user:devices:S001"), eq(DEVICE_ID), any(Double.class));
    }

    @Test
    @DisplayName("刷新令牌：Lua 脚本返回 FAIL 时，判定为重放攻击，记录日志并强制下线")
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

        // 验证异常文案（已更新为强制下线）
        assertEquals("安全警告：检测到异常刷新操作，已被强制下线，请重新登录！", e.getMessage());
        
        // 验证记录审计日志
        verify(logMapper).insert(any(SysLog.class));
        
        // 验证执行了强制下线的 Revoke 脚本（调用类型为 Long）
        verify(redis).execute(ArgumentMatchers.<DefaultRedisScript<Long>>any(), anyList(), anyString(), anyString());
        
        // 验证移除了设备记录
        verify(zSetOps).remove("user:devices:S001", DEVICE_ID);
    }

    @Test
    @DisplayName("登出：调用原子脚本清理主 Token 与幂等缓存，并移除设备")
    void revokeTokensClearsIdempotentCache() {
        String accessToken = JwtUtil.generateAccessToken("S001", "student", DEVICE_ID);
        String tokenWithBearer = "Bearer " + accessToken;

        service.revokeTokens(tokenWithBearer);

        verify(redis).execute(any(DefaultRedisScript.class), anyList());
        verify(zSetOps).remove("user:devices:S001", DEVICE_ID);
    }

    @Test
    @DisplayName("登出：Redis 发生异常时应抛出 BusinessException，防止假登出")
    void revokeTokensFailsOnRedisError() {
        String accessToken = JwtUtil.generateAccessToken("S001", "student", DEVICE_ID);
        String tokenWithBearer = "Bearer " + accessToken;

        doThrow(new RedisConnectionFailureException("Redis down"))
                .when(redis).execute(any(DefaultRedisScript.class), anyList());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.revokeTokens(tokenWithBearer));

        assertEquals("登出失败，网络或缓存异常，请重试！", e.getMessage());
    }

    @Test
    @DisplayName("未登录不能获取 WS 票据")
    void wsTicketRequiresLogin() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.issueWsTicket(null, null));
        assertEquals("未登录，无法获取票据", e.getMessage());
    }
}