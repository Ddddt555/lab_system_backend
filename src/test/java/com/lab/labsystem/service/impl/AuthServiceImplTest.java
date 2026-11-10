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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

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
 *
 * <p>覆盖三类高风险行为：
 * <ol>
 *   <li>锁定策略的先后顺序（锁定检查在验证码之前）；</li>
 *   <li>失败计数的累加与达到阈值时的提示替换；</li>
 *   <li>刷新令牌的类型校验与 Redis 一致性校验。</li>
 * </ol>
 */
class AuthServiceImplTest {

    private StudentMapper studentMapper;
    private CaptchaService captchaService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private AuthServiceImpl service;

    @BeforeAll
    static void initJwt() {
        // JwtUtil 通过 @Value 注入密钥，这里手动初始化以便签发/解析令牌
        JwtUtil util = new JwtUtil();
        util.setSecret("0123456789abcdef0123456789abcdef");
        // 过期时间同样是 @Value 注入的静态字段，不设置会签发出"立即过期"的令牌
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
        when(redis.opsForValue()).thenReturn(ops);

        service = new AuthServiceImpl(
                studentMapper,
                mock(TeacherMapper.class),
                mock(SysAdminMapper.class),
                mock(AdminService.class),
                mock(BCryptPasswordEncoder.class),
                mock(SysLogMapper.class),
                captchaService,
                redis,
        		mock(org.springframework.core.env.Environment.class)); 
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
                () -> service.loginStudent("S001", "pwd", "k", "c", request()));

        assertEquals("密码错误次数过多，账号已被锁定 10 分钟，请稍后再试！", e.getMessage());
        verify(captchaService, never()).verifyCaptcha(anyString(), anyString());
    }

    @Test
    @DisplayName("验证码错误时直接拒绝")
    void rejectBadCaptcha() {
        when(ops.get(anyString())).thenReturn(null);
        when(captchaService.verifyCaptcha("k", "c")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.loginStudent("S001", "pwd", "k", "c", request()));

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
                () -> service.loginStudent("S404", "pwd", "k", "c", request()));

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
                () -> service.loginStudent("S001", "bad", "k", "c", request()));

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
                () -> service.loginStudent("S001", "bad", "k", "c", request()));

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

        Map<String, Object> data = service.loginStudent("S001", "good", "k", "c", request());

        assertNotNull(data.get("token"));
        assertNotNull(data.get("refreshToken"));
        assertNotNull(data.get("user"));
        verify(ops).set(eq("refresh_token:S001"), anyString(), any());
        verify(redis).delete("login:fail:student:S001");
    }

    @Test
    @DisplayName("刷新令牌类型必须是 refresh")
    void refreshRejectsAccessToken() {
        String accessToken = JwtUtil.generateAccessToken("S001", "student");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refreshToken(accessToken));

        assertEquals("无效的刷新令牌类型！", e.getMessage());
    }

    @Test
    @DisplayName("刷新令牌与 Redis 不一致时拒绝")
    void refreshRejectsMismatch() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student");
        when(ops.get("refresh_token:S001")).thenReturn("another-token");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refreshToken(refreshToken));

        assertEquals("刷新令牌已失效，请重新登录！", e.getMessage());
    }

    @Test
    @DisplayName("刷新成功时返回新的 access token 并续期")
    void refreshSuccess() {
        String refreshToken = JwtUtil.generateRefreshToken("S001", "student");
        when(ops.get("refresh_token:S001")).thenReturn(refreshToken);

        Map<String, Object> data = service.refreshToken(refreshToken);

        assertNotNull(data.get("token"));
        verify(redis).expire(eq("refresh_token:S001"), any());
    }

    @Test
    @DisplayName("未登录不能获取 WS 票据")
    void wsTicketRequiresLogin() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.issueWsTicket(null, null));
        assertEquals("未登录，无法获取票据", e.getMessage());
    }

    private void setEncoder(BCryptPasswordEncoder encoder) {
        // 用反射替换 passwordEncoder，避免为了测试重写构造参数顺序
        org.springframework.test.util.ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
    }
}
