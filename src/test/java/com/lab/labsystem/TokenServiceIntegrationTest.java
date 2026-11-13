package com.lab.labsystem;

import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.service.auth.TokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
)
@AutoConfigureTestRestTemplate
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TokenServiceIntegrationTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StringRedisTemplate realRedisTemplate;

    @Autowired
    private TestRestTemplate restTemplate;

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
    @DisplayName("2. 真实 Redis 真并发：多线程同时刷新，验证原子性与幂等")
    void testTrueConcurrentRefresh() throws Exception {
        String hashTag = "{" + userNo + ":" + deviceId + "}";
        String oldRefreshToken = realRedisTemplate.opsForValue().get("refresh_token:" + hashTag);

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    tokenService.refreshToken(oldRefreshToken, deviceId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(0, failCount.get(), "真并发刷新不应出现异常");
        assertEquals(threads, successCount.get(), "所有并发请求都应成功");
        assertTrue(realRedisTemplate.hasKey("refresh:idem:" + hashTag));
    }

    @Test
    @Order(3)
    @DisplayName("3. 登出后验证旧 Access Token 被拦截（完整 HTTP 链路闭环）")
    void testLogoutInvalidatesAccessToken() {
        String hashTag = "{" + userNo + ":" + deviceId + "}";
        String oldRefreshToken = realRedisTemplate.opsForValue().get("refresh_token:" + hashTag);
        
        Map<String, Object> result = tokenService.refreshToken(oldRefreshToken, deviceId);
        String accessToken = (String) result.get("token");

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("X-Device-Id", deviceId);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> beforeResponse = restTemplate.exchange("/api/notice/list", HttpMethod.GET, entity, String.class);
        assertEquals(200, beforeResponse.getStatusCode().value(), "登出前，携带有效 Token 应能成功访问");

        tokenService.revokeTokens("Bearer " + accessToken);

        assertFalse(realRedisTemplate.hasKey("refresh_token:" + hashTag));
        assertFalse(realRedisTemplate.hasKey("refresh:idem:" + hashTag));

        ResponseEntity<String> afterResponse = restTemplate.exchange("/api/notice/list", HttpMethod.GET, entity, String.class);
        assertEquals(401, afterResponse.getStatusCode().value(), "登出后，旧 Access Token 应被拦截返回 401");
    }

    @Test
    @Order(4)
    @DisplayName("4. 零容忍策略验证：旧 Token 重放将撤销该设备当前有效会话")
    void testReplayAttackRevokesAllSessions() {
        String hashTag = "{" + userNo + ":" + deviceId + "}";
        
        // 清理环境，重新生成一对初始 Token
        realRedisTemplate.delete(Arrays.asList(
                "refresh_token:" + hashTag,
                "refresh:idem:" + hashTag
        ));
        Map<String, Object> initialTokens = tokenService.createTokenPair(userNo, role, deviceId);
        String oldRefreshToken = (String) initialTokens.get("refreshToken");

        // 用户正常刷新，拿到了第二对 Token
        Map<String, Object> latestTokens = tokenService.refreshToken(oldRefreshToken, deviceId);
        String currentValidRefreshToken = (String) latestTokens.get("refreshToken");
        assertEquals(currentValidRefreshToken, realRedisTemplate.opsForValue().get("refresh_token:" + hashTag));

        // 手动清除幂等缓存（模拟 5 分钟时间窗口已过）
        // 如果不清理，此时用旧 token 会命中缓存返回成功，不会触发重放拦截
        realRedisTemplate.delete("refresh:idem:" + hashTag);

        // 攻击者（或延迟的请求）拿着已被替换的旧 Token 来重放
        assertThrows(com.lab.labsystem.common.BusinessException.class, 
                () -> tokenService.refreshToken(oldRefreshToken, deviceId));

        // 核心断言：零容忍策略生效！不仅拒绝重放，当前最新的有效 Token 也一并被销毁
        assertFalse(realRedisTemplate.hasKey("refresh_token:" + hashTag), "重放攻击触发后，当前有效的 Refresh Token 必须被强制销毁");
        assertFalse(realRedisTemplate.hasKey("refresh:idem:" + hashTag), "重放攻击触发后，幂等缓存必须被强制销毁");
    }
}