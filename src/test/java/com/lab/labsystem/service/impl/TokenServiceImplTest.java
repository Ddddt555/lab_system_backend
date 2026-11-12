package com.lab.labsystem.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        verify(ops).set(eq("refresh_token:S001:" + DEVICE_ID), anyString(), any());
        verify(zSetOps).add(eq("user:devices:S001"), eq(DEVICE_ID), any(Double.class));
    }

    @Test
    @DisplayName("刷新令牌：命中并发幂等缓存时，直接返回缓存中的新 Token")
    void refreshConflictReturnsCachedToken() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);

        // 构造幂等缓存中保存的 Token 字符串
        String cachedData = "cached-new-access|||cached-new-refresh";
        when(ops.get(anyString())).thenReturn(cachedData);

        Map<String, Object> data = service.refreshToken(refreshToken, DEVICE_ID);

        assertEquals("cached-new-access", data.get("token"));
        assertEquals("cached-new-refresh", data.get("refreshToken"));

        // 验证命中缓存后，跳过了后续的 Lua 脚本比对
        verify(redis, never()).execute(any(), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("刷新令牌：Lua 失败且未命中缓存，重放攻击直接拒绝")
    void refreshRejectsMismatch() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);

        // 模拟缓存未命中
        when(ops.get(anyString())).thenReturn(null);

        // 模拟 Redis 中的 RefreshToken 与请求不一致
        when(redis.execute(
                ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                anyList(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(0L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refreshToken(refreshToken, DEVICE_ID));

        assertEquals("安全警告：检测到异常刷新操作，已被强制下线，请重新登录！", e.getMessage());
        // 验证异常拦截，不执行缓存写入
        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("刷新令牌：成功时返回新的双 Token，并写入幂等缓存")
    void refreshSuccess() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);

        // 模拟缓存未命中
        when(ops.get(anyString())).thenReturn(null);

        // 模拟 Lua 脚本原子替换成功
        when(redis.execute(
                ArgumentMatchers.<DefaultRedisScript<Long>>any(),
                anyList(),
                anyString(),
                anyString(),
                anyString()
        )).thenReturn(1L);

        Map<String, Object> data = service.refreshToken(refreshToken, DEVICE_ID);

        assertNotNull(data.get("token"));
        assertNotNull(data.get("refreshToken"));

        // 验证成功替换后，新 Token 对被写入了幂等缓存
        verify(ops).set(anyString(), anyString(), any(java.time.Duration.class));
        verify(zSetOps).add(eq("user:devices:S001"), eq(DEVICE_ID), any(Double.class));
    }

    @Test
    @DisplayName("未登录不能获取 WS 票据")
    void wsTicketRequiresLogin() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.issueWsTicket(null, null));
        assertEquals("未登录，无法获取票据", e.getMessage());
    }
}