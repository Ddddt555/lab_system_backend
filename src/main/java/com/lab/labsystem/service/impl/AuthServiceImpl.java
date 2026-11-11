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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAIL_COUNT = 5;
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String WS_TICKET_PREFIX = "ws:ticket:";
    private static final String USER_DEVICES_PREFIX = "user:devices:"; // 记录用户活跃设备

    private static final int MAX_DEVICES = 5; // 最大允许同时在线的设备数

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
    private final Environment environment;

    @Value("${app.security.test-login-enabled:false}")
    private boolean testLoginEnabled;

    public AuthServiceImpl(StudentMapper studentMapper,
                           TeacherMapper teacherMapper,
                           SysAdminMapper sysAdminMapper,
                           AdminService adminService,
                           BCryptPasswordEncoder passwordEncoder,
                           SysLogMapper logMapper,
                           CaptchaService captchaService,
                           StringRedisTemplate stringRedisTemplate,
                           Environment environment) {
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
    public Map<String, Object> loginStudent(String username, String password,
                                            String captchaKey, String captchaCode,
                                            String deviceId, HttpServletRequest request) {
        checkAccountLocked("student", username);
        verifyCaptcha(captchaKey, captchaCode);

        Student student = studentMapper.selectOne(
                new QueryWrapper<Student>().eq("student_no", username));
        if (student == null) {
            saveLoginLog("学生-" + username, "登录失败：学号不存在", request);
            throw new BusinessException("学号或密码错误！");
        }

        if (!passwordEncoder.matches(password, student.getPassword())) {
            saveLoginLog("学生-" + username, "登录失败：密码错误", request);
            recordLoginFail("student", username);
            throw new BusinessException("学号或密码错误！");
        }

        clearLoginFail("student", username);
        saveLoginLog("学生-" + username, "登录成功", request);

        return issueTokens(student.getStudentNo(), student.getName(), "student",
                isMustChange(student.getMustChangePassword()), deviceId);
    }

    @Override
    public Map<String, Object> loginTeacher(String username, String password,
                                            String captchaKey, String captchaCode,
                                            String deviceId, HttpServletRequest request) {
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
                isMustChange(teacher.getMustChangePassword()), deviceId);
    }

    @Override
    public Map<String, Object> loginAdmin(String username, String password,
                                          String captchaKey, String captchaCode,
                                          String deviceId, HttpServletRequest request) {
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

        return issueTokens(username, "超级管理员", "admin", mustChange, deviceId);
    }

    @Override
    public Map<String, Object> testLogin(String username, String role, HttpServletRequest request) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Not Found");
        }

        if (!testLoginEnabled) {
            throw new BusinessException("【测试接口已禁用】请勿在生产环境尝试非法登录！");
        }

        if (!"student".equals(role) && !"teacher".equals(role) && !"admin".equals(role)) {
            throw new BusinessException("非法的角色类型！");
        }

        String deviceId = "test-device";
        String accessToken = JwtUtil.generateAccessToken(username, role, deviceId);
        String refreshToken = JwtUtil.generateRefreshToken(username, role, deviceId);
        stringRedisTemplate.opsForValue()
                .set(REFRESH_TOKEN_PREFIX + username + ":" + deviceId, refreshToken, REFRESH_TOKEN_TTL);

        Map<String, Object> data = new HashMap<>();
        data.put("user", new LoginUserVO(username, "测试用户-" + username, role));
        data.put("token", accessToken);
        data.put("refreshToken", refreshToken);

        saveLoginLog("测试登录-" + username, "使用免验证码通道", request);
        return data;
    }

    @Override
    public String logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Claims claims = JwtUtil.parseToken(token);
                String jti = claims.getId();
                String userNo = claims.getSubject();
                String deviceId = claims.get("deviceId", String.class);
                Date expiration = claims.getExpiration();
                long remainingSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

                if (remainingSeconds > 0 && jti != null) {
                    stringRedisTemplate.opsForValue().set(
                            BLACKLIST_PREFIX + jti, "1", Duration.ofSeconds(remainingSeconds));
                }
                if (userNo != null && deviceId != null) {
                    // 清理当前设备的 RT
                    stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + userNo + ":" + deviceId);
                    // 从活跃设备列表中移除
                    stringRedisTemplate.opsForZSet().remove(USER_DEVICES_PREFIX + userNo, deviceId);
                }
            } catch (Exception ignored) {
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
    public Map<String, Object> refreshToken(String refreshToken, String deviceId) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException("缺少刷新令牌！");
        }
        try {
            Claims claims = JwtUtil.parseToken(refreshToken);
            String userNo = claims.getSubject();
            String role = (String) claims.get("role");
            String type = (String) claims.get("type");
            String tokenDeviceId = (String) claims.get("deviceId");

            if (!"refresh".equals(type)) {
                throw new BusinessException("无效的刷新令牌类型！");
            }

            if (tokenDeviceId != null && deviceId != null && !tokenDeviceId.equals(deviceId)) {
                throw new BusinessException("设备环境异常，请重新登录！");
            }

            String redisKey = REFRESH_TOKEN_PREFIX + userNo + ":" + deviceId;
            String cachedToken = stringRedisTemplate.opsForValue().get(redisKey);
            
            if (cachedToken == null) {
                throw new BusinessException("刷新令牌已失效，请重新登录！");
            }

            if (!cachedToken.equals(refreshToken)) {
                stringRedisTemplate.delete(redisKey);
                
                SysLog securityLog = new SysLog();
                securityLog.setOperator("用户-" + userNo);
                securityLog.setAction("安全拦截：设备 " + deviceId + " 检测到无效刷新令牌，已强制该设备下线");
                securityLog.setCreateTime(LocalDateTime.now());
                securityLog.setSuccess(0);
                logMapper.insert(securityLog);

                throw new BusinessException("安全警告：检测到异常刷新操作，已被强制下线，请重新登录！");
            }

            stringRedisTemplate.delete(redisKey);

            String newAccessToken = JwtUtil.generateAccessToken(userNo, role, deviceId);
            String newRefreshToken = JwtUtil.generateRefreshToken(userNo, role, deviceId);

            stringRedisTemplate.opsForValue()
                    .set(redisKey, newRefreshToken, REFRESH_TOKEN_TTL);

            // 刷新时更新活跃时间，防止因为太久没刷新被视为“最旧设备”
            stringRedisTemplate.opsForZSet().add(USER_DEVICES_PREFIX + userNo, deviceId, System.currentTimeMillis());
            // 顺便延长设备列表的 TTL，确保与 RT 一致
            stringRedisTemplate.expire(USER_DEVICES_PREFIX + userNo, REFRESH_TOKEN_TTL);

            Map<String, Object> data = new HashMap<>();
            data.put("token", newAccessToken);
            data.put("refreshToken", newRefreshToken); 
            return data;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("刷新令牌无效或已过期！");
        }
    }

    private void verifyCaptcha(String captchaKey, String captchaCode) {
        if (!captchaService.verifyCaptcha(captchaKey, captchaCode)) {
            throw new BusinessException("验证码错误或已过期，请重新输入！");
        }
    }

    private boolean isMustChange(Integer flag) {
        return flag != null && flag == 1;
    }

    private Map<String, Object> issueTokens(String userNo, String name, String role, boolean mustChange, String deviceId) {
        String accessToken = JwtUtil.generateAccessToken(userNo, role, deviceId);
        String refreshToken = JwtUtil.generateRefreshToken(userNo, role, deviceId);
        String redisKey = REFRESH_TOKEN_PREFIX + userNo + ":" + deviceId;
        
        // 1. 记录当前设备活跃时间到 ZSet
        String userDevicesKey = USER_DEVICES_PREFIX + userNo;
        long now = System.currentTimeMillis();
        stringRedisTemplate.opsForZSet().add(userDevicesKey, deviceId, now);
        // 刷新设备列表的过期时间
        stringRedisTemplate.expire(userDevicesKey, REFRESH_TOKEN_TTL);

        // 2. 检查是否超过最大设备限制
        Long deviceCount = stringRedisTemplate.opsForZSet().zCard(userDevicesKey);
        if (deviceCount != null && deviceCount > MAX_DEVICES) {
            // 3. 获取登录时间最早的设备（ZSet 中 score 最小，排在第一位）
            Set<String> oldestDevices = stringRedisTemplate.opsForZSet().range(userDevicesKey, 0, 0);
            if (oldestDevices != null && !oldestDevices.isEmpty()) {
                String oldestDeviceId = oldestDevices.iterator().next();
                
                // 4. 从设备列表中移除
                stringRedisTemplate.opsForZSet().remove(userDevicesKey, oldestDeviceId);
                // 5. 删除旧设备的 Refresh Token，旧设备下次刷新时会因为 RT 失效而掉线
                stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + userNo + ":" + oldestDeviceId);
                
                log.warn("用户 {} 的在线设备数超过 {} 台，已自动踢出最旧设备: {}", userNo, MAX_DEVICES, oldestDeviceId);
            }
        }

        // 6. 保存新设备的 Refresh Token
        stringRedisTemplate.opsForValue().set(redisKey, refreshToken, REFRESH_TOKEN_TTL);

        LoginUserVO userVO = new LoginUserVO(userNo, name, role);
        userVO.setMustChangePassword(mustChange);

        Map<String, Object> data = new HashMap<>();
        data.put("user", userVO);
        data.put("token", accessToken);
        data.put("refreshToken", refreshToken);
        return data;
    }

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