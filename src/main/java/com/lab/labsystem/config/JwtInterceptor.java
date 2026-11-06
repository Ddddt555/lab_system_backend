package com.lab.labsystem.config;

import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.SysAdmin;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j; // 🛑 新增
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j // 🛑 开启日志
public class JwtInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final SysAdminMapper sysAdminMapper;

    public JwtInterceptor(StringRedisTemplate stringRedisTemplate, 
                          StudentMapper studentMapper, 
                          TeacherMapper teacherMapper, 
                          SysAdminMapper sysAdminMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.sysAdminMapper = sysAdminMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            String paramToken = request.getParameter("token");
            if (paramToken != null && !paramToken.isEmpty()) {
                token = paramToken;
            }
        }

        if (token != null) {
            try {
                Claims claims = JwtUtil.parseToken(token);

                // 【安全加固】只接受 Access Token。
                // Refresh Token 只能用于 /api/refresh-token 换取新的 Access Token；
                // 否则 7 天有效期的 Refresh Token 可被直接当作 Access Token 使用，
                // 使 30 分钟的短效 Access Token 机制完全失效。
                if (!"access".equals(claims.get("type"))) {
                    log.warn("🚫 拒绝使用非 Access Token 访问接口: {}, type={}",
                            request.getRequestURI(), claims.get("type"));
                    response.setStatus(401);
                    return false;
                }

                String jti = claims.getId();
                
                if (jti != null && Boolean.TRUE.equals(stringRedisTemplate.hasKey("jwt:blacklist:" + jti))) {
                    log.warn("🚫 试图使用已被拉黑的 Token 访问接口: {}", request.getRequestURI());
                    response.setStatus(401);
                    return false;
                }

                String userNo = claims.getSubject();
                String role = (String) claims.get("role");

                request.setAttribute("currentUserNo", userNo);
                request.setAttribute("currentUserRole", role);

                // =========================================================
                // 🛑【核心防线】检查用户是否需要强制修改密码
                // =========================================================
                boolean mustChange = false;
                if ("student".equals(role)) {
                    Student s = studentMapper.selectById(userNo);
                    mustChange = (s != null && s.getMustChangePassword() != null && s.getMustChangePassword() == 1);
                } else if ("teacher".equals(role)) {
                    Teacher t = teacherMapper.selectById(userNo);
                    mustChange = (t != null && t.getMustChangePassword() != null && t.getMustChangePassword() == 1);
                } else if ("admin".equals(role)) {
                    SysAdmin a = sysAdminMapper.selectById(userNo);
                    mustChange = (a != null && a.getMustChangePassword() != null && a.getMustChangePassword() == 1);
                }

                String uri = request.getRequestURI();
                boolean isWhitelisted = uri.endsWith("/update-password") || 
                                        uri.endsWith("/notice/list") || 
                                        uri.endsWith("/logout");

                if (mustChange && !isWhitelisted) {
                    log.warn("⚠️ 用户 {} 未修改初始密码，被强制拦截访问: {}", userNo, uri);
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"msg\":\"请先修改初始密码！\",\"data\":null}");
                    return false;
                }

                return true; 
                
            } catch (Exception e) {
                log.error("❌ 解析 Token 失败: {}", e.getMessage());
                response.setStatus(401);
                return false;
            }
        }
        
        // =========================================================
        // ⚠️ 3. 安全规范轨：尝试解析短时效一次性票据 (Ticket)
        // =========================================================
        String ticket = request.getParameter("ticket");
        if (ticket != null && !ticket.isEmpty()) {
            String redisKey = "report:ticket:" + ticket;
            String userInfo = stringRedisTemplate.opsForValue().get(redisKey);
            if (userInfo != null) {
                stringRedisTemplate.delete(redisKey);
                String[] parts = userInfo.split(":");
                request.setAttribute("currentUserNo", parts[0]);
                request.setAttribute("currentUserRole", parts[1]);
                
                if (parts.length > 2) {
                    request.setAttribute("ticketFileName", parts[2]);
                }
                
                return true;
            } else {
                log.warn("🚫 试图使用无效或已过期的 Ticket 访问接口: {}", request.getRequestURI());
            }
        }
        
        response.setStatus(401);
        return false;
    }
}