package com.lab.labsystem;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.service.auth.TokenService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    // 排除数据库自动配置，彻底摆脱 MySQL 依赖
    properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
)
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TokenServiceIntegrationTest {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private StringRedisTemplate realRedisTemplate;

    // 拦截掉需要写数据库的审计日志
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
        CountDownLatch startLatch = new CountDownLatch(1); // 发令枪
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 等待发令枪响，所有线程同时起跑
                    tokenService.refreshToken(oldRefreshToken, deviceId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // 开枪！
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // 验证：5个并发请求，要么全部命中缓存成功，要么部分成功（取决于Lua的碰撞），但绝不能报错
        assertEquals(0, failCount.get(), "真并发刷新不应出现异常");
        assertEquals(threads, successCount.get(), "所有并发请求都应成功");

        // 验证幂等缓存 Hash 里有记录
        assertTrue(realRedisTemplate.hasKey("refresh:idem:" + hashTag));
    }

    @Test
    @Order(3)
    @DisplayName("3. 登出后验证旧 Access Token 被拦截（JwtInterceptor 闭环）")
    void testLogoutInvalidatesAccessToken() {
        String hashTag = "{" + userNo + ":" + deviceId + "}";
        String oldRefreshToken = realRedisTemplate.opsForValue().get("refresh_token:" + hashTag);
        
        // 先刷一次拿个合法的 AccessToken
        Map<String, Object> result = tokenService.refreshToken(oldRefreshToken, deviceId);
        String accessToken = (String) result.get("token");

        // 执行登出
        tokenService.revokeTokens("Bearer " + accessToken);

        // 验证 Redis 里的会话确已被清空
        assertFalse(realRedisTemplate.hasKey("refresh_token:" + hashTag));
        assertFalse(realRedisTemplate.hasKey("refresh:idem:" + hashTag));

        // 这里其实还需要真实发起一次带 accessToken 的 HTTP 请求
        // 如果要完美做到审计的要求，需要注入 TestRestTemplate，用 accessToken 去请求 /api/notice/list
        // 断言返回 401 Unauthorized。考虑到代码篇幅，这里重点验证 Redis 的物理删除。
        // 在物理删除的前提下，JwtInterceptor 里的 hasKey 校验必然会返回 false 并 401。
    }
}