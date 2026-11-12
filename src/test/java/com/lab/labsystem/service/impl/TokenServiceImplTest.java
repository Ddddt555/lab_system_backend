package com.lab.labsystem.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
        
        // 验证是否写入了 Redis
        verify(ops).set(ArgumentMatchers.eq("refresh_token:S001:" + DEVICE_ID), anyString(), any());
        verify(zSetOps).add(ArgumentMatchers.eq("user:devices:S001"), ArgumentMatchers.eq(DEVICE_ID), any(Double.class));
    }

    @Test
    @DisplayName("刷新令牌：类型必须是 refresh")
    void refreshRejectsAccessToken() {
        String accessToken = JwtUtil.generateAccessToken("S001", "student", DEVICE_ID);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refreshToken(accessToken, DEVICE_ID));

        assertEquals("无效的刷新令牌类型！", e.getMessage());
    }

    @Test
    @DisplayName("刷新令牌：Redis 中确实为空（失效），报错并强制下线，不执行删除")
    void refreshRejectsMismatch() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);
        
        // Mock Lua 脚本返回 0 (代表比对失败)
        when(redis.execute(
                ArgumentMatchers.<DefaultRedisScript<Long>>any(), 
                anyList(), 
                anyString(), 
                anyString(), 
                anyString()
        )).thenReturn(0L);

        // 模拟 Redis 中确实没有值（过期/注销）
        when(ops.get("refresh_token:S001:" + DEVICE_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refreshToken(refreshToken, DEVICE_ID));

        assertEquals("安全警告：检测到异常刷新操作，已被强制下线，请重新登录！", e.getMessage());
        
        // 验证不再执行无脑 delete 操作
        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("刷新令牌：并发冲突时返回 Redis 中的最新 Token，不执行删除")
    void refreshConflictReturnsLatestToken() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);
        
        // Mock Lua 脚本返回 0 (代表用旧 Token 比对失败)
        when(redis.execute(
                ArgumentMatchers.<DefaultRedisScript<Long>>any(), 
                anyList(), 
                anyString(), 
                anyString(), 
                anyString()
        )).thenReturn(0L);

        // 模拟并发场景：其他线程已经抢先刷新，Redis 里存的是新的 Token
        String latestTokenInRedis = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);
        when(ops.get("refresh_token:S001:" + DEVICE_ID)).thenReturn(latestTokenInRedis);

        // 断言：不抛异常，直接返回 Redis 里的最新 Token，保证用户不掉线
        Map<String, Object> data = service.refreshToken(refreshToken, DEVICE_ID);

        assertNotNull(data.get("token"));
        assertEquals(latestTokenInRedis, data.get("refreshToken"));
        
        // 验证绝不执行 delete，避免误杀有效的 Token
        verify(redis, never()).delete(anyString());
    }

    @Test
    @DisplayName("刷新令牌：成功时返回新的双 Token 并续期")
    void refreshSuccess() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);
        
        // Mock Lua 脚本返回 1 (代表比对并替换成功)
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
        
        // 验证是否更新了活跃设备时间
        verify(zSetOps).add(ArgumentMatchers.eq("user:devices:S001"), ArgumentMatchers.eq(DEVICE_ID), any(Double.class));
    }

    @Test
    @DisplayName("未登录不能获取 WS 票据")
    void wsTicketRequiresLogin() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.issueWsTicket(null, null));
        assertEquals("未登录，无法获取票据", e.getMessage());
    }
}