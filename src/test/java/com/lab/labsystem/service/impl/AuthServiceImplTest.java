package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.CaptchaService;
import com.lab.labsystem.dto.request.LoginDTO;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.admin.AdminService;
import com.lab.labsystem.service.auth.impl.AuthServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

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
 * 认证核心逻辑的单元测试。
 * 覆盖验证码校验、防爆破锁定、查库比对密码等核心流程。
 */
class AuthServiceImplTest {

    private StudentMapper studentMapper;
    private CaptchaService captchaService;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private Environment environment;
    private AuthServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        studentMapper = mock(StudentMapper.class);
        captchaService = mock(CaptchaService.class);
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(ops);

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

    private LoginDTO buildLoginDTO(String username, String password, String captchaKey, String captchaCode) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setCaptchaKey(captchaKey);
        dto.setCaptchaCode(captchaCode);
        dto.setDeviceId("test-device-1234567890");
        return dto;
    }

    @Test
    @DisplayName("账号已锁定时直接拒绝，不再校验验证码")
    void rejectWhenLocked() {
        when(ops.get("login:fail:student:S001")).thenReturn("5");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verifyStudent(buildLoginDTO("S001", "pwd", "k", "c"), request()));

        assertEquals("账号已被锁定，请稍后再试", e.getMessage());
        verify(captchaService, never()).verifyCaptcha(anyString(), anyString());
    }

    @Test
    @DisplayName("验证码错误时直接拒绝，不查询数据库")
    void rejectBadCaptcha() {
        when(ops.get(anyString())).thenReturn(null);
        when(captchaService.verifyCaptcha("k", "c")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verifyStudent(buildLoginDTO("S001", "pwd", "k", "c"), request()));

        assertEquals("验证码错误或已过期", e.getMessage());
        verify(studentMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("账号不存在时提示与密码错误一致（防枚举）")
    void rejectUnknownUser() {
        when(ops.get(anyString())).thenReturn(null);
        when(captchaService.verifyCaptcha("k", "c")).thenReturn(true);
        when(studentMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verifyStudent(buildLoginDTO("S404", "pwd", "k", "c"), request()));

        assertEquals("账号或密码错误", e.getMessage());
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
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.verifyStudent(buildLoginDTO("S001", "bad", "k", "c"), request()));

        assertEquals("账号或密码错误", e.getMessage());
        verify(redis).expire(eq("login:fail:student:S001"), any());
    }

    @Test
    @DisplayName("登录成功返回用户信息，并清空失败计数")
    void verifySuccess() {
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
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);

        var userVO = service.verifyStudent(buildLoginDTO("S001", "good", "k", "c"), request());

        assertNotNull(userVO);
        assertEquals("S001", userVO.getUserNo());
        assertEquals("张三", userVO.getName());
        verify(redis).delete("login:fail:student:S001");
    }

    @Test
    @DisplayName("prod 环境下拒绝测试登录")
    void rejectTestLoginInProd() {
        when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.verifyTestLogin("S001", "student", request()));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, e.getStatusCode());
    }
}