package com.lab.labsystem;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.service.auth.TokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TokenServiceIntegrationTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StringRedisTemplate realRedisTemplate;

    // 将依赖数据库的 Mapper 拦截掉，测试环境不再强依赖 MySQL
    @MockitoBean
    private SysLogMapper logMapper;

    private final String userNo = "TestUser";
    private final String role = "student";
    private final String deviceId = "TestDevice";

    @BeforeAll
    void cleanUp() {
        String hashTag = "{" + userNo + ":" + deviceId + "}";
        realRedisTemplate.delete(Arrays.asList(
                "refresh_token:" + hashTag,
                "refresh:idem:" + hashTag
        ));
        realRedisTemplate.delete("user:devices:" + userNo);
    }

    @Test
    @Order(1)
    @DisplayName("1. 真实 Redis：创建 Token 并验证写入")
    void testCreateTokenPair() {
        Map<String, Object> tokens = tokenService.createTokenPair(userNo, role, deviceId);
        assertNotNull(tokens.get("refreshToken"));

        String hashTag = "{" + userNo + ":" + deviceId + "}";
        assertTrue(realRedisTemplate.hasKey("refresh_token:" + hashTag));
    }

    @Test
    @Order(2)
    @DisplayName("2. 真实 Redis：并发刷新命中幂等缓存")
    void testConcurrentRefresh() {
        String hashTag = "{" + userNo + ":" + deviceId + "}";
        String oldRefreshToken = realRedisTemplate.opsForValue().get("refresh_token:" + hashTag);

        // 模拟并发：连续两次使用旧 Token 刷新
        Map<String, Object> result1 = tokenService.refreshToken(oldRefreshToken, deviceId);
        Map<String, Object> result2 = tokenService.refreshToken(oldRefreshToken, deviceId);

        // 验证：两次拿到的 Token 必须完全一致（说明第二次触发了 CACHED 分支）
        assertEquals(result1.get("token"), result2.get("token"));
        assertEquals(result1.get("refreshToken"), result2.get("refreshToken"));
    }

    @Test
    @Order(3)
    @DisplayName("3. 真实 Redis：登出后彻底销毁会话，拒绝重放")
    void testLogoutAndPreventReplay() {
        String hashTag = "{" + userNo + ":" + deviceId + "}";
        String oldRefreshToken = realRedisTemplate.opsForValue().get("refresh_token:" + hashTag);
        String currentAccessToken = tokenService.refreshToken(oldRefreshToken, deviceId).get("token").toString();

        // 执行登出
        tokenService.revokeTokens("Bearer " + currentAccessToken);

        // 验证主 Token 和 幂等缓存 Hash 全部被删除
        assertFalse(realRedisTemplate.hasKey("refresh_token:" + hashTag));
        assertFalse(realRedisTemplate.hasKey("refresh:idem:" + hashTag));

        // 验证重放攻击被拦截（此时主 Token 和缓存都没了）
        BusinessException e = assertThrows(BusinessException.class,
                () -> tokenService.refreshToken(oldRefreshToken, deviceId));
        
        assertEquals("安全警告：检测到异常刷新操作，已被强制下线，请重新登录！", e.getMessage());
    }
}