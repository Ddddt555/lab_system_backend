package com.lab.labsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.common.CaptchaService;
import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.SysAdmin;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.AdminService;
import com.lab.labsystem.vo.LoginUserVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "登录认证接口", description = "包含验证码获取、学生登录、教师登录、管理员登录、退出登录")
@RestController
@RequestMapping("/api")
public class LoginController {

    @Autowired private StudentMapper studentMapper;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private SysAdminMapper sysAdminMapper;
    @Autowired private AdminService adminService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private SysLogMapper logMapper;
    @Autowired private CaptchaService captchaService;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    @Value("${app.security.test-login-enabled:false}")
    private boolean testLoginEnabled;

    // ⚠️ 防爆破配置
    private static final int MAX_FAIL_COUNT = 5;
    private static final String LOGIN_FAIL_PREFIX = "login:fail:";

    // 🛑【核心修复】增强登录日志记录，支持 IP、URI、请求方法等
    private void saveLoginLog(String operator, String action, HttpServletRequest request) {
        SysLog log = new SysLog();
        log.setOperator(operator);
        log.setAction(action);
        log.setCreateTime(LocalDateTime.now());
        
        // 自动判断成功还是失败
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

    // 检查是否被锁定
    private void checkAccountLocked(String role, String username) {
        String key = LOGIN_FAIL_PREFIX + role + ":" + username;
        String countStr = stringRedisTemplate.opsForValue().get(key);
        if (countStr != null && Integer.parseInt(countStr) >= MAX_FAIL_COUNT) {
            throw new RuntimeException("密码错误次数过多，账号已被锁定 10 分钟，请稍后再试！");
        }
    }

    // 记录失败次数
    private void recordLoginFail(String role, String username) {
        String key = LOGIN_FAIL_PREFIX + role + ":" + username;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null) {
            if (count == 1) {
                stringRedisTemplate.expire(key, Duration.ofMinutes(10));
            }
            if (count >= MAX_FAIL_COUNT) {
                throw new RuntimeException("密码错误次数过多，账号已被锁定 10 分钟，请稍后再试！");
            }
        }
    }

    // 清除失败次数（登录成功时调用）
    private void clearLoginFail(String role, String username) {
        stringRedisTemplate.delete(LOGIN_FAIL_PREFIX + role + ":" + username);
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public Result<Object> getCaptcha() {
        return Result.success(captchaService.generateCaptcha());
    }

    @Operation(summary = "学生登录")
    @PostMapping("/login/student")
    public Result<Object> studentLogin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String captchaKey,
            @RequestParam String captchaCode,
            HttpServletRequest request) {

        try {
            checkAccountLocked("student", username);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }

        if (!captchaService.verifyCaptcha(captchaKey, captchaCode)) {
            return Result.error("验证码错误或已过期，请重新输入！");
        }

        Student student = studentMapper.selectOne(new QueryWrapper<Student>().eq("student_no", username));
        if (student == null) {
            saveLoginLog("学生-" + username, "登录失败：学号不存在", request);
            return Result.error("学号不存在！");
        }

        if (!passwordEncoder.matches(password, student.getPassword())) {
            saveLoginLog("学生-" + username, "登录失败：密码错误", request);
            try {
                recordLoginFail("student", username);
            } catch (RuntimeException e) {
                return Result.error(e.getMessage());
            }
            return Result.error("密码错误！");
        }

        clearLoginFail("student", username);
        saveLoginLog("学生-" + username, "登录成功", request);

        // 🛑【核心改造】生成双 Token
        String accessToken = JwtUtil.generateAccessToken(student.getStudentNo(), "student");
        String refreshToken = JwtUtil.generateRefreshToken(student.getStudentNo(), "student");
        
        // 🛑 将 Refresh Token 存入 Redis，有效期 7 天
        stringRedisTemplate.opsForValue().set("refresh_token:" + student.getStudentNo(), refreshToken, Duration.ofDays(7));

        Map<String, Object> data = new HashMap<>();
        LoginUserVO userVO = new LoginUserVO(student.getStudentNo(), student.getName(), "student");
        userVO.setMustChangePassword(student.getMustChangePassword() != null && student.getMustChangePassword() == 1);
        
        data.put("user", userVO);
        data.put("token", accessToken);
        data.put("refreshToken", refreshToken);

        return Result.success(data);
    }

    @Operation(summary = "教师登录")
    @PostMapping("/login/teacher")
    public Result<Object> teacherLogin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String captchaKey,
            @RequestParam String captchaCode,
            HttpServletRequest request) {

        try {
            checkAccountLocked("teacher", username);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }

        if (!captchaService.verifyCaptcha(captchaKey, captchaCode)) {
            return Result.error("验证码错误或已过期，请重新输入！");
        }

        Teacher teacher = teacherMapper.selectOne(new QueryWrapper<Teacher>().eq("work_no", username));
        if (teacher == null) {
            saveLoginLog("教师-" + username, "登录失败：工号不存在", request);
            return Result.error("工号不存在！");
        }

        if (!passwordEncoder.matches(password, teacher.getPassword())) {
            saveLoginLog("教师-" + username, "登录失败：密码错误", request);
            try {
                recordLoginFail("teacher", username);
            } catch (RuntimeException e) {
                return Result.error(e.getMessage());
            }
            return Result.error("密码错误！");
        }

        clearLoginFail("teacher", username);
        saveLoginLog("教师-" + username, "登录成功", request);

        // 🛑【核心改造】生成双 Token
        String accessToken = JwtUtil.generateAccessToken(teacher.getWorkNo(), "teacher");
        String refreshToken = JwtUtil.generateRefreshToken(teacher.getWorkNo(), "teacher");
        
        // 🛑 将 Refresh Token 存入 Redis，有效期 7 天
        stringRedisTemplate.opsForValue().set("refresh_token:" + teacher.getWorkNo(), refreshToken, Duration.ofDays(7));

        Map<String, Object> data = new HashMap<>();
        LoginUserVO userVO = new LoginUserVO(teacher.getWorkNo(), teacher.getName(), "teacher");
        userVO.setMustChangePassword(teacher.getMustChangePassword() != null && teacher.getMustChangePassword() == 1);
        
        data.put("user", userVO);
        data.put("token", accessToken);
        data.put("refreshToken", refreshToken);

        return Result.success(data);
    }

    @Operation(summary = "管理员登录")
    @PostMapping("/login/admin")
    public Result<Object> adminLogin(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String captchaKey,
            @RequestParam String captchaCode,
            HttpServletRequest request) {

        try {
            checkAccountLocked("admin", username);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }

        if (!captchaService.verifyCaptcha(captchaKey, captchaCode)) {
            return Result.error("验证码错误或已过期，请重新输入！");
        }

        if (adminService.checkLogin(username, password)) {
            clearLoginFail("admin", username);
            saveLoginLog("管理员-" + username, "登录成功", request);

            // 🛑【核心改造】生成双 Token
            String accessToken = JwtUtil.generateAccessToken(username, "admin");
            String refreshToken = JwtUtil.generateRefreshToken(username, "admin");
            
            // 🛑 将 Refresh Token 存入 Redis，有效期 7 天
            stringRedisTemplate.opsForValue().set("refresh_token:" + username, refreshToken, Duration.ofDays(7));

            Map<String, Object> data = new HashMap<>();
            SysAdmin admin = sysAdminMapper.selectOne(new QueryWrapper<SysAdmin>().eq("username", username));
            LoginUserVO userVO = new LoginUserVO(username, "超级管理员", "admin");
            userVO.setMustChangePassword(admin != null && admin.getMustChangePassword() != null && admin.getMustChangePassword() == 1);
            
            data.put("user", userVO);
            data.put("token", accessToken);
            data.put("refreshToken", refreshToken);

            return Result.success(data);
        }

        saveLoginLog("管理员-" + username, "登录失败：账号或密码错误", request);
        try {
            recordLoginFail("admin", username);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
        return Result.error("管理员账号或密码错误！");
    }

    @Operation(summary = "【测试专用】免验证码登录")
    @PostMapping("/login/test")
    public Result<Object> testLogin(
            @RequestParam String username,
            @RequestParam String role,
            HttpServletRequest request) {
        
        String activeProfile = System.getProperty("spring.profiles.active");
        if (activeProfile == null) {
            activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        if ("prod".equals(activeProfile)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Not Found");
        }

        if (!testLoginEnabled) {
            return Result.error("【测试接口已禁用】请勿在生产环境尝试非法登录！");
        }
        
        if (!"student".equals(role) && !"teacher".equals(role) && !"admin".equals(role)) {
            return Result.error("非法的角色类型！");
        }

        // 🛑【核心改造】测试接口也返回双 Token
        String accessToken = JwtUtil.generateAccessToken(username, role);
        String refreshToken = JwtUtil.generateRefreshToken(username, role);
        stringRedisTemplate.opsForValue().set("refresh_token:" + username, refreshToken, Duration.ofDays(7));

        Map<String, Object> data = new HashMap<>();
        data.put("user", new LoginUserVO(username, "测试用户-" + username, role));
        data.put("token", accessToken);
        data.put("refreshToken", refreshToken);

        saveLoginLog("测试登录-" + username, "使用免验证码通道", request);
        return Result.success(data);
    }

    @Operation(summary = "退出登录", description = "将当前 Token 加入 Redis 黑名单，使其立即失效")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Claims claims = JwtUtil.parseToken(token);
                String jti = claims.getId();
                String userNo = claims.getSubject();
                java.util.Date expiration = claims.getExpiration();
                long remainingSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

                if (remainingSeconds > 0 && jti != null) {
                    stringRedisTemplate.opsForValue().set(
                            "jwt:blacklist:" + jti, 
                            "1", 
                            Duration.ofSeconds(remainingSeconds)
                    );
                }
                // 🛑 退出时顺便清理 Redis 中的 Refresh Token
                if (userNo != null) {
                    stringRedisTemplate.delete("refresh_token:" + userNo);
                }
            } catch (Exception e) {
                // 忽略异常
            }
        }
        return Result.success("退出成功");
    }
    
    @Operation(summary = "获取WebSocket连接票据", description = "用于替代URL传JWT，有效期30秒")
    @GetMapping("/ws-ticket")
    public Result<String> getWsTicket(HttpServletRequest request) {
        String userNo = (String) request.getAttribute("currentUserNo");
        String role = (String) request.getAttribute("currentUserRole"); 
        
        if (userNo == null) {
            return Result.error("未登录，无法获取票据");
        }

        String ticket = java.util.UUID.randomUUID().toString().replace("-", "");
        String userInfo = userNo + ":" + role;
        stringRedisTemplate.opsForValue().set("ws:ticket:" + ticket, userInfo, java.time.Duration.ofSeconds(30));
        
        return Result.success(ticket);
    }

    // =========================================================
    // 🛑【核心新增】无感刷新 Access Token 接口
    // =========================================================
    @Operation(summary = "刷新 Access Token", description = "使用 Refresh Token 换取新的 Access Token")
    @PostMapping("/refresh-token")
    public Result<Object> refreshToken(@RequestParam String refreshToken) {
        try {
            // 1. 解析 Refresh Token
            Claims claims = JwtUtil.parseToken(refreshToken);
            String userNo = claims.getSubject();
            String role = (String) claims.get("role");
            String type = (String) claims.get("type");

            // 2. 必须是 refresh 类型，防止拿 Access Token 来刷新
            if (!"refresh".equals(type)) {
                return Result.error("无效的刷新令牌类型！");
            }

            // 3. 校验 Redis 中的 Refresh Token 是否一致（防止已被登出或伪造）
            String redisKey = "refresh_token:" + userNo;
            String cachedToken = stringRedisTemplate.opsForValue().get(redisKey);
            if (cachedToken == null || !cachedToken.equals(refreshToken)) {
                return Result.error("刷新令牌已失效，请重新登录！");
            }

            // 4. 签发新的 Access Token
            String newAccessToken = JwtUtil.generateAccessToken(userNo, role);
            
            // 5. 刷新 Refresh Token 的 Redis 过期时间（滑动过期，只要你在用就一直有效）
            stringRedisTemplate.expire(redisKey, Duration.ofDays(7));

            Map<String, Object> data = new HashMap<>();
            data.put("token", newAccessToken);
            return Result.success(data);

        } catch (Exception e) {
            return Result.error("刷新令牌无效或已过期！");
        }
    }
}