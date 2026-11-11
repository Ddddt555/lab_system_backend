package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.CaptchaService;
import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录链路的单元测试。
 */
class AuthServiceImplTest {

    /** 测试用设备 ID，与 Redis Key 拼接保持一致 */
    private static final String DEVICE_ID = "test-device";

    private StudentMapper studentMapper;
    private CaptchaService captchaService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ZSetOperations<String, String> zSetOps;
    private Environment environment;
    private AuthServiceImpl service;

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
        studentMapper = mock(StudentMapper.class);
        captchaService = mock(CaptchaService.class);
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        zSetOps = mock(ZSetOperations.class);

        when(redis.opsForValue()).thenReturn(ops);
        // 关键修复：补齐 ZSetOperations 的 mock，避免 opsForZSet() 返回 null
        when(redis.opsForZSet()).thenReturn(zSetOps);

        environment = mock(Environment.class);
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(false);

        service = new AuthServiceImpl(
                studentMapper,
                mock(TeacherMapper.class),
                mock(SysAdminMapper.class),
                mock(AdminService.class),
                mock(BCryptPasswordEncoder.class),
                mock(SysLogMapper.class),
                captchaService,
                redis,
                environment);
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/login/student");
        return request;
    }

    @Test
    @DisplayName("账号已锁定时直接拒绝，不再校验验证码")
    void rejectWhenLocked() {
        when(ops.get("login:fail:student:S001")).thenReturn("5");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginStudent("S001", "pwd", "k", "c", DEVICE_ID, request()));

        assertEquals("密码错误次数过多，账号已被锁定 10 分钟，请稍后再试！", e.getMessage());
        verify(captchaService, never()).verifyCaptcha(anyString(), anyString());
    }

    @Test
    @DisplayName("验证码错误时直接拒绝")
    void rejectBadCaptcha() {
        when(ops.get(anyString())).thenReturn(null);
        when(captchaService.verifyCaptcha("k", "c")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginStudent("S001", "pwd", "k", "c", DEVICE_ID, request()));

        assertEquals("验证码错误或已过期，请重新输入！", e.getMessage());
        verify(studentMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("账号不存在时提示与密码错误一致（防枚举）")
    void rejectUnknownUser() {
        when(ops.get(anyString())).thenReturn(null);
        when(captchaService.verifyCaptcha("k", "c")).thenReturn(true);
        when(studentMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginStudent("S404", "pwd", "k", "c", DEVICE_ID, request()));

        assertEquals("学号或密码错误！", e.getMessage());
    }

    @Test
    @DisplayName("密码错误时累加失败次数，首次失败设置 10 分钟过期")
    void recordFailOnWrongPassword() {
        when(ops.get(anyString())).thenReturn(null);
        when(captchaService.verifyCaptcha("k", "c")).thenReturn(true);
        Student student = new Student();
        student.setStudentNo("S001");
        student.setPassword("hash");
        when(studentMapper.selectOne(any())).thenReturn(student);
        when(ops.increment("login:fail:student:S001")).thenReturn(1L);

        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        when(encoder.matches("bad", "hash")).thenReturn(false);
        setEncoder(encoder);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginStudent("S001", "bad", "k", "c", DEVICE_ID, request()));

        assertEquals("学号或密码错误！", e.getMessage());
        verify(redis).expire(eq("login:fail:student:S001"), any());
    }

    @Test
    @DisplayName("失败次数达到阈值时返回锁定提示")
    void lockOnThreshold() {
        when(ops.get(anyString())).thenReturn(null);
        when(captchaService.verifyCaptcha("k", "c")).thenReturn(true);
        Student student = new Student();
        student.setStudentNo("S001");
        student.setPassword("hash");
        when(studentMapper.selectOne(any())).thenReturn(student);
        when(ops.increment("login:fail:student:S001")).thenReturn(5L);

        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        when(encoder.matches("bad", "hash")).thenReturn(false);
        setEncoder(encoder);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginStudent("S001", "bad", "k", "c", DEVICE_ID, request()));

        assertEquals("密码错误次数过多，账号已被锁定 10 分钟，请稍后再试！", e.getMessage());
    }

    @Test
    @DisplayName("登录成功：签发双 Token、写 Redis、清空失败计数")
    void loginSuccess() {
        when(ops.get(anyString())).thenReturn(null);
        when(captchaService.verifyCaptcha("k", "c")).thenReturn(true);
        Student student = new Student();
        student.setStudentNo("S001");
        student.setName("张三");
        student.setPassword("hash");
        student.setMustChangePassword(1);
        when(studentMapper.selectOne(any())).thenReturn(student);

        BCryptPasswordEncoder encoder = mock(BCryptPasswordEncoder.class);
        when(encoder.matches("good", "hash")).thenReturn(true);
        setEncoder(encoder);

        Map<String, Object> data = service.loginStudent("S001", "good", "k", "c", DEVICE_ID, request());

        assertNotNull(data.get("token"));
        assertNotNull(data.get("refreshToken"));
        assertNotNull(data.get("user"));
        // Redis Key 现在带上了 deviceId
        verify(ops).set(eq("refresh_token:S001:" + DEVICE_ID), anyString(), any());
        verify(redis).delete("login:fail:student:S001");
    }

    @Test
    @DisplayName("刷新令牌类型必须是 refresh")
    void refreshRejectsAccessToken() {
        String accessToken = JwtUtil.generateAccessToken("S001", "student", DEVICE_ID);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refreshToken(accessToken, DEVICE_ID));

        assertEquals("无效的刷新令牌类型！", e.getMessage());
    }

    @Test
    @DisplayName("刷新令牌与 Redis 不一致时拒绝")
    void refreshRejectsMismatch() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);
        when(ops.get("refresh_token:S001:" + DEVICE_ID)).thenReturn("another-token");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refreshToken(refreshToken, DEVICE_ID));

        assertEquals("安全警告：检测到异常刷新操作，已被强制下线，请重新登录！", e.getMessage());
    }

    @Test
    @DisplayName("刷新成功时返回新的双 Token 并续期")
    void refreshSuccess() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student", DEVICE_ID);
        when(ops.get("refresh_token:S001:" + DEVICE_ID)).thenReturn(refreshToken);

        Map<String, Object> data = service.refreshToken(refreshToken, DEVICE_ID);

        assertNotNull(data.get("token"));
        assertNotNull(data.get("refreshToken"));
        // 现在写的是带 deviceId 的 Key
        verify(ops).set(eq("refresh_token:S001:" + DEVICE_ID), anyString(), any());
    }

    @Test
    @DisplayName("未登录不能获取 WS 票据")
    void wsTicketRequiresLogin() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.issueWsTicket(null, null));
        assertEquals("未登录，无法获取票据", e.getMessage());
    }

    @Test
    @DisplayName("prod 环境下拒绝测试登录")
    void rejectTestLoginInProd() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.testLogin("S001", "student", request()));

        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }

    @Test
    @DisplayName("未开启 test-login 时拒绝测试登录")
    void rejectTestLoginWhenDisabled() {
        ReflectionTestUtils.setField(service, "testLoginEnabled", false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.testLogin("S001", "student", request()));

        assertEquals("【测试接口已禁用】请勿在生产环境尝试非法登录！", e.getMessage());
    }

    @DisplayName("非法角色被拒绝")
    @Test
    void rejectTestLoginWithUnknownRole() {
        ReflectionTestUtils.setField(service, "testLoginEnabled", true);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.testLogin("X1", "guest", request()));

        assertEquals("非法的角色类型！", e.getMessage());
    }

    private void setEncoder(BCryptPasswordEncoder encoder) {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
    }
}