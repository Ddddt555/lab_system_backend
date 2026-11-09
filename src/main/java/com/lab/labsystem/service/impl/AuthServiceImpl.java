package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.CaptchaService;
import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.SysAdmin;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.AdminService;
import com.lab.labsystem.service.AuthService;
import com.lab.labsystem.vo.LoginUserVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    // ===== 防爆破配置 =====
    private static final int MAX_FAIL_COUNT = 5;
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";

    // ===== Redis 键 =====
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String WS_TICKET_PREFIX = "ws:ticket:";

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);
    private static final Duration WS_TICKET_TTL = Duration.ofSeconds(30);

    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final SysAdminMapper sysAdminMapper;
    private final AdminService adminService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SysLogMapper logMapper;
    private final CaptchaService captchaService;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${app.security.test-login-enabled:false}")
    private boolean testLoginEnabled;

    public AuthServiceImpl(StudentMapper studentMapper,
                           TeacherMapper teacherMapper,
                           SysAdminMapper sysAdminMapper,
                           AdminService adminService,
                           BCryptPasswordEncoder passwordEncoder,
                           SysLogMapper logMapper,
                           CaptchaService captchaService,
                           StringRedisTemplate stringRedisTemplate) {
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.sysAdminMapper = sysAdminMapper;
        this.adminService = adminService;
        this.passwordEncoder = passwordEncoder;
        this.logMapper = logMapper;
        this.captchaService = captchaService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Object generateCaptcha() {
        return captchaService.generateCaptcha();
    }

    // ========================= 学生 / 教师登录 =========================

    @Override
    public Map<String, Object> loginStudent(String username, String password,
                                            String captchaKey, String captchaCode,
                                            HttpServletRequest request) {
        checkAccountLocked("student", username);
        verifyCaptcha(captchaKey, captchaCode);

        Student student = studentMapper.selectOne(
                new QueryWrapper<Student>().eq("student_no", username));
        if (student == null) {
            saveLoginLog("学生-" + username, "登录失败：学号不存在", request);
            // 对外统一提示，避免通过返回文案枚举出有效学号
            throw new BusinessException("学号或密码错误！");
        }

        if (!passwordEncoder.matches(password, student.getPassword())) {
            saveLoginLog("学生-" + username, "登录失败：密码错误", request);
            recordLoginFail("student", username); // 达到阈值会抛锁定提示
            throw new BusinessException("学号或密码错误！");
        }

        clearLoginFail("student", username);
        saveLoginLog("学生-" + username, "登录成功", request);

        return issueTokens(student.getStudentNo(), student.getName(), "student",
                isMustChange(student.getMustChangePassword()));
    }

    @Override
    public Map<String, Object> loginTeacher(String username, String password,
                                            String captchaKey, String captchaCode,
                                            HttpServletRequest request) {
        checkAccountLocked("teacher", username);
        verifyCaptcha(captchaKey, captchaCode);

        Teacher teacher = teacherMapper.selectOne(
                new QueryWrapper<Teacher>().eq("work_no", username));
        if (teacher == null) {
            saveLoginLog("教师-" + username, "登录失败：工号不存在", request);
            throw new BusinessException("工号或密码错误！");
        }

        if (!passwordEncoder.matches(password, teacher.getPassword())) {
            saveLoginLog("教师-" + username, "登录失败：密码错误", request);
            recordLoginFail("teacher", username);
            throw new BusinessException("工号或密码错误！");
        }

        clearLoginFail("teacher", username);
        saveLoginLog("教师-" + username, "登录成功", request);

        return issueTokens(teacher.getWorkNo(), teacher.getName(), "teacher",
                isMustChange(teacher.getMustChangePassword()));
    }

    // ========================= 管理员登录 =========================

    @Override
    public Map<String, Object> loginAdmin(String username, String password,
                                          String captchaKey, String captchaCode,
                                          HttpServletRequest request) {
        checkAccountLocked("admin", username);
        verifyCaptcha(captchaKey, captchaCode);

        if (!adminService.checkLogin(username, password)) {
            saveLoginLog("管理员-" + username, "登录失败：账号或密码错误", request);
            recordLoginFail("admin", username);
            throw new BusinessException("管理员账号或密码错误！");
        }

        clearLoginFail("admin", username);
        saveLoginLog("管理员-" + username, "登录成功", request);

        SysAdmin admin = sysAdminMapper.selectOne(
                new QueryWrapper<SysAdmin>().eq("username", username));
        boolean mustChange = admin != null && isMustChange(admin.getMustChangePassword());

        return issueTokens(username, "超级管理员", "admin", mustChange);
    }

    // ========================= 测试登录 =========================

    @Override
    public Map<String, Object> testLogin(String username, String role, HttpServletRequest request) {
        String activeProfile = System.getProperty("spring.profiles.active");
        if (activeProfile == null) {
            activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        if ("prod".equals(activeProfile)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Not Found");
        }

        if (!testLoginEnabled) {
            throw new BusinessException("【测试接口已禁用】请勿在生产环境尝试非法登录！");
        }

        if (!"student".equals(role) && !"teacher".equals(role) && !"admin".equals(role)) {
            throw new BusinessException("非法的角色类型！");
        }

        String accessToken = JwtUtil.generateAccessToken(username, role);
        String refreshToken = JwtUtil.generateRefreshToken(username, role);
        stringRedisTemplate.opsForValue()
                .set(REFRESH_TOKEN_PREFIX + username, refreshToken, REFRESH_TOKEN_TTL);

        Map<String, Object> data = new HashMap<>();
        data.put("user", new LoginUserVO(username, "测试用户-" + username, role));
        data.put("token", accessToken);
        data.put("refreshToken", refreshToken);

        saveLoginLog("测试登录-" + username, "使用免验证码通道", request);
        return data;
    }

    // ========================= 退出 / WS 票据 / 刷新 =========================

    @Override
    public String logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Claims claims = JwtUtil.parseToken(token);
                String jti = claims.getId();
                String userNo = claims.getSubject();
                Date expiration = claims.getExpiration();
                long remainingSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

                if (remainingSeconds > 0 && jti != null) {
                    stringRedisTemplate.opsForValue().set(
                            BLACKLIST_PREFIX + jti, "1", Duration.ofSeconds(remainingSeconds));
                }
                // 退出时顺便清理 Redis 中的 Refresh Token
                if (userNo != null) {
                    stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + userNo);
                }
            } catch (Exception ignored) {
                // 令牌本身已失效时无需处理
            }
        }
        return "退出成功";
    }

    @Override
    public String issueWsTicket(String userNo, String role) {
        if (userNo == null) {
            throw new BusinessException("未登录，无法获取票据");
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue()
                .set(WS_TICKET_PREFIX + ticket, userNo + ":" + role, WS_TICKET_TTL);
        return ticket;
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException("缺少刷新令牌！");
        }
        try {
            Claims claims = JwtUtil.parseToken(refreshToken);
            String userNo = claims.getSubject();
            String role = (String) claims.get("role");
            String type = (String) claims.get("type");

            // 必须是 refresh 类型，防止拿 Access Token 来刷新
            if (!"refresh".equals(type)) {
                throw new BusinessException("无效的刷新令牌类型！");
            }

            // 与 Redis 中保存的比对，防止已被登出或伪造
            String redisKey = REFRESH_TOKEN_PREFIX + userNo;
            String cachedToken = stringRedisTemplate.opsForValue().get(redisKey);
            if (cachedToken == null || !cachedToken.equals(refreshToken)) {
                throw new BusinessException("刷新令牌已失效，请重新登录！");
            }

            String newAccessToken = JwtUtil.generateAccessToken(userNo, role);

            // 滑动过期：只要还在用就续期
            stringRedisTemplate.expire(redisKey, REFRESH_TOKEN_TTL);

            Map<String, Object> data = new HashMap<>();
            data.put("token", newAccessToken);
            return data;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("刷新令牌无效或已过期！");
        }
    }

    // ========================= 私有辅助 =========================

    private void verifyCaptcha(String captchaKey, String captchaCode) {
        if (!captchaService.verifyCaptcha(captchaKey, captchaCode)) {
            throw new BusinessException("验证码错误或已过期，请重新输入！");
        }
    }

    private boolean isMustChange(Integer flag) {
        return flag != null && flag == 1;
    }

    /** 签发双 Token 并写入 Redis，返回前端需要的结构。 */
    private Map<String, Object> issueTokens(String userNo, String name, String role, boolean mustChange) {
        String accessToken = JwtUtil.generateAccessToken(userNo, role);
        String refreshToken = JwtUtil.generateRefreshToken(userNo, role);
        stringRedisTemplate.opsForValue()
                .set(REFRESH_TOKEN_PREFIX + userNo, refreshToken, REFRESH_TOKEN_TTL);

        LoginUserVO userVO = new LoginUserVO(userNo, name, role);
        userVO.setMustChangePassword(mustChange);

        Map<String, Object> data = new HashMap<>();
        data.put("user", userVO);
        data.put("token", accessToken);
        data.put("refreshToken", refreshToken);
        return data;
    }

    /** 记录登录日志（成功/失败由 action 文案推断）。 */
    private void saveLoginLog(String operator, String action, HttpServletRequest request) {
        SysLog log = new SysLog();
        log.setOperator(operator);
        log.setAction(action);
        log.setCreateTime(LocalDateTime.now());
        log.setSuccess(action.contains("成功") ? 1 : 0);

        if (request != null) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            log.setIp(ip);
            log.setMethod(request.getMethod());
            log.setUri(request.getRequestURI());
            log.setCostMs(0L);
        }
        logMapper.insert(log);
    }

    private void checkAccountLocked(String role, String username) {
        String key = LOGIN_FAIL_PREFIX + role + ":" + username;
        String countStr = stringRedisTemplate.opsForValue().get(key);
        if (countStr != null && Integer.parseInt(countStr) >= MAX_FAIL_COUNT) {
            throw new BusinessException("密码错误次数过多，账号已被锁定 10 分钟，请稍后再试！");
        }
    }

    private void recordLoginFail(String role, String username) {
        String key = LOGIN_FAIL_PREFIX + role + ":" + username;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null) {
            if (count == 1) {
                stringRedisTemplate.expire(key, LOCK_TTL);
            }
            if (count >= MAX_FAIL_COUNT) {
                throw new BusinessException("密码错误次数过多，账号已被锁定 10 分钟，请稍后再试！");
            }
        }
    }

    private void clearLoginFail(String role, String username) {
        stringRedisTemplate.delete(LOGIN_FAIL_PREFIX + role + ":" + username);
    }
}
