package com.lab.labsystem.service.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.CaptchaService;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.dto.request.LoginDTO;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.SysAdmin;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.admin.AdminService;
import com.lab.labsystem.service.auth.AuthService;
import com.lab.labsystem.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final SysAdminMapper sysAdminMapper;
    private final AdminService adminService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SysLogMapper logMapper;
    private final CaptchaService captchaService;
    private final StringRedisTemplate stringRedisTemplate;
    private final Environment environment;

    @Value("${app.security.test-login-enabled:false}")
    private boolean testLoginEnabled;

    public AuthServiceImpl(StudentMapper studentMapper, TeacherMapper teacherMapper, SysAdminMapper sysAdminMapper,
                           AdminService adminService, BCryptPasswordEncoder passwordEncoder, SysLogMapper logMapper,
                           CaptchaService captchaService, StringRedisTemplate stringRedisTemplate, Environment environment) {
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.sysAdminMapper = sysAdminMapper;
        this.adminService = adminService;
        this.passwordEncoder = passwordEncoder;
        this.logMapper = logMapper;
        this.captchaService = captchaService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.environment = environment;
    }

    @Override
    public Object generateCaptcha() {
        return captchaService.generateCaptcha();
    }

    @Override
    public LoginUserVO verifyStudent(LoginDTO dto, HttpServletRequest request) {
        String username = dto.getUsername();
        checkAccountLocked("student", username);
        verifyCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        Student student = studentMapper.selectOne(new QueryWrapper<Student>().eq("student_no", username));
        if (student == null) {
            saveLoginLog("学生-" + username, "登录失败：学号不存在", request);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(dto.getPassword(), student.getPassword())) {
            saveLoginLog("学生-" + username, "登录失败：密码错误", request);
            recordLoginFail("student", username);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        clearLoginFail("student", username);
        saveLoginLog("学生-" + username, "登录成功", request);

        LoginUserVO vo = new LoginUserVO(student.getStudentNo(), student.getName(), "student");
        vo.setMustChangePassword(isMustChange(student.getMustChangePassword()));
        return vo;
    }

    @Override
    public LoginUserVO verifyTeacher(LoginDTO dto, HttpServletRequest request) {
        String username = dto.getUsername();
        checkAccountLocked("teacher", username);
        verifyCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        Teacher teacher = teacherMapper.selectOne(new QueryWrapper<Teacher>().eq("work_no", username));
        if (teacher == null) {
            saveLoginLog("教师-" + username, "登录失败：工号不存在", request);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        if (!passwordEncoder.matches(dto.getPassword(), teacher.getPassword())) {
            saveLoginLog("教师-" + username, "登录失败：密码错误", request);
            recordLoginFail("teacher", username);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        clearLoginFail("teacher", username);
        saveLoginLog("教师-" + username, "登录成功", request);

        LoginUserVO vo = new LoginUserVO(teacher.getWorkNo(), teacher.getName(), "teacher");
        vo.setMustChangePassword(isMustChange(teacher.getMustChangePassword()));
        return vo;
    }

    @Override
    public LoginUserVO verifyAdmin(LoginDTO dto, HttpServletRequest request) {
        String username = dto.getUsername();
        checkAccountLocked("admin", username);
        verifyCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        if (!adminService.checkLogin(username, dto.getPassword())) {
            saveLoginLog("管理员-" + username, "登录失败：账号或密码错误", request);
            recordLoginFail("admin", username);
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        clearLoginFail("admin", username);
        saveLoginLog("管理员-" + username, "登录成功", request);

        SysAdmin admin = sysAdminMapper.selectOne(new QueryWrapper<SysAdmin>().eq("username", username));
        LoginUserVO vo = new LoginUserVO(username, "超级管理员", "admin");
        vo.setMustChangePassword(admin != null && isMustChange(admin.getMustChangePassword()));
        return vo;
    }

    @Override
    public LoginUserVO verifyTestLogin(String username, String role, HttpServletRequest request) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Not Found");
        }
        if (!testLoginEnabled) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "【测试接口已禁用】请勿在生产环境尝试非法登录！");
        }
        if (!"student".equals(role) && !"teacher".equals(role) && !"admin".equals(role)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "非法的角色类型！");
        }
        saveLoginLog("测试登录-" + username, "使用免验证码通道", request);
        return new LoginUserVO(username, "测试用户-" + username, role);
    }

    private void verifyCaptcha(String captchaKey, String captchaCode) {
        if (!captchaService.verifyCaptcha(captchaKey, captchaCode)) {
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR);
        }
    }

    private boolean isMustChange(Integer flag) {
        return flag != null && flag == 1;
    }

    private void saveLoginLog(String operator, String action, HttpServletRequest request) {
        SysLog log = new SysLog();
        log.setOperator(operator);
        log.setAction(action);
        log.setCreateTime(LocalDateTime.now());
        log.setSuccess(action.contains("成功") ? 1 : 0);
        if (request != null) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) ip = request.getRemoteAddr();
            log.setIp(ip);
            log.setMethod(request.getMethod());
            log.setUri(request.getRequestURI());
            log.setCostMs(0L);
        }
        logMapper.insert(log);
    }

    private void checkAccountLocked(String role, String username) {
        String countStr = stringRedisTemplate.opsForValue().get(LOGIN_FAIL_PREFIX + role + ":" + username);
        if (countStr != null && Integer.parseInt(countStr) >= MAX_FAIL_COUNT) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    private void recordLoginFail(String role, String username) {
        String key = LOGIN_FAIL_PREFIX + role + ":" + username;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null) {
            if (count == 1) stringRedisTemplate.expire(key, LOCK_TTL);
            if (count >= MAX_FAIL_COUNT) throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    private void clearLoginFail(String role, String username) {
        stringRedisTemplate.delete(LOGIN_FAIL_PREFIX + role + ":" + username);
    }
}