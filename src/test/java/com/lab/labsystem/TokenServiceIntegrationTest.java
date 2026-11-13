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
        
        // 先刷一次拿个合法的 AccessToken
        Map<String, Object> result = tokenService.refreshToken(oldRefreshToken, deviceId);
        String accessToken = (String) result.get("token");

        // 构造 HTTP 请求头，携带合法 Token 和 DeviceId
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("X-Device-Id", deviceId);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        // 1. 验证登出前，访问受保护接口是畅通的
        ResponseEntity<String> beforeResponse = restTemplate.exchange("/api/notice/list", HttpMethod.GET, entity, String.class);
        assertEquals(200, beforeResponse.getStatusCode().value(), "登出前，携带有效 Token 应能成功访问");

        // 2. 执行登出操作
        tokenService.revokeTokens("Bearer " + accessToken);

        // 3. 验证 Redis 里的会话确已被清空
        assertFalse(realRedisTemplate.hasKey("refresh_token:" + hashTag));
        assertFalse(realRedisTemplate.hasKey("refresh:idem:" + hashTag));

        // 4. 验证登出后，携带同一个 Access Token 再次访问，应被 JwtInterceptor 拦截返回 401
        ResponseEntity<String> afterResponse = restTemplate.exchange("/api/notice/list", HttpMethod.GET, entity, String.class);
        assertEquals(401, afterResponse.getStatusCode().value(), "登出后，旧 Access Token 应被拦截返回 401");
    }
}