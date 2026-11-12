package com.lab.labsystem.config;

import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.service.auth.MustChangePasswordCache;
import com.lab.labsystem.service.auth.TokenService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final StringRedisTemplate stringRedisTemplate;
    private final MustChangePasswordCache mustChangePasswordCache;

    public JwtInterceptor(TokenService tokenService,
                          StringRedisTemplate stringRedisTemplate,
                          MustChangePasswordCache mustChangePasswordCache) {
        this.tokenService = tokenService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mustChangePasswordCache = mustChangePasswordCache;
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

                // 校验令牌类型，仅允许 Access Token 访问业务接口
                if (!"access".equals(claims.get("type"))) {
                    log.warn("拒绝使用非 Access Token 访问接口: {}, type={}",
                            request.getRequestURI(), claims.get("type"));
                    response.setStatus(401);
                    return false;
                }

                String jti = claims.getId();
                // 校验黑名单（调用 TokenService）
                if (tokenService.isBlacklisted(jti)) {
                    log.warn("试图使用已被拉黑的 Token 访问接口: {}", request.getRequestURI());
                    response.setStatus(401);
                    return false;
                }

                // 校验设备指纹，防止 Token 被跨设备盗用
                String tokenDeviceId = (String) claims.get("deviceId");
                String requestDeviceId = request.getHeader("X-Device-Id");
                if (tokenDeviceId != null && requestDeviceId != null && !tokenDeviceId.equals(requestDeviceId)) {
                    log.warn("设备指纹不匹配，疑似 Token 盗用。Token: {}, Request: {}", tokenDeviceId, requestDeviceId);
                    response.setStatus(401);
                    return false;
                }

                String userNo = claims.getSubject();
                String role = (String) claims.get("role");

                request.setAttribute("currentUserNo", userNo);
                request.setAttribute("currentUserRole", role);

                // 校验用户是否需要强制修改密码
                boolean mustChange = mustChangePasswordCache.isRequired(role, userNo);
                String uri = request.getRequestURI();
                boolean isWhitelisted = uri.endsWith("/update-password") || 
                                        uri.endsWith("/notice/list") || 
                                        uri.endsWith("/logout");

                if (mustChange && !isWhitelisted) {
                    log.warn("用户 {} 未修改初始密码，被强制拦截访问: {}", userNo, uri);
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"msg\":\"请先修改初始密码！\",\"data\":null}");
                    return false;
                }

                return true; 
                
            } catch (Exception e) {
                log.error("解析 Token 失败: {}", e.getMessage());
                response.setStatus(401);
                return false;
            }
        }
        
        // 解析短时效一次性票据 (Ticket)
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
                log.warn("试图使用无效或已过期的 Ticket 访问接口: {}", request.getRequestURI());
            }
        }
        
        response.setStatus(401);
        return false;
    }
}