package com.lab.labsystem.config;

import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.service.auth.MustChangePasswordCache;
import com.lab.labsystem.service.auth.TokenService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

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

        String clientIp = getClientIp(request);
        String requestUri = request.getRequestURI();

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        if (token != null) {
            try {
                Claims claims = JwtUtil.parseToken(token);

                if (!"access".equals(claims.get("type"))) {
                    securityLog.warn("【非法访问】尝试使用非 Access Token 访问接口。URI: {}, IP: {}", requestUri, clientIp);
                    response.setStatus(401);
                    return false;
                }

                String jti = claims.getId();
                if (tokenService.isBlacklisted(jti)) {
                    securityLog.warn("【非法访问】尝试使用已被拉黑的 Token 访问接口。URI: {}, IP: {}", requestUri, clientIp);
                    response.setStatus(401);
                    return false;
                }

                String tokenDeviceId = (String) claims.get("deviceId");
                String requestDeviceId = request.getHeader("X-Device-Id");
                
                if (tokenDeviceId == null || requestDeviceId == null || requestDeviceId.trim().isEmpty()) {
                    securityLog.warn("【非法访问】请求未携带 X-Device-Id 头或 Token 缺失设备信息。URI: {}, IP: {}", requestUri, clientIp);
                    response.setStatus(401);
                    return false;
                }
                
                if (!tokenDeviceId.equals(requestDeviceId)) {
                    securityLog.warn("【设备异常】Token 设备指纹不匹配。URI: {}, Token设备: {}, 请求设备: {}, IP: {}", 
                            requestUri, tokenDeviceId, requestDeviceId, clientIp);
                    response.setStatus(401);
                    return false;
                }

                String userNo = claims.getSubject();
                String role = (String) claims.get("role");

                // 校验该设备是否已被登出（通过检查刷新令牌是否还存在）
                String hashTag = "{" + userNo + ":" + tokenDeviceId + "}";
                String refreshTokenKey = "refresh_token:" + hashTag;
                if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(refreshTokenKey))) {
                    securityLog.warn("【会话已撤销】设备已被登出或踢下线，Access Token 强制失效。用户: {}, 设备: {}, URI: {}", 
                            userNo, tokenDeviceId, requestUri);
                    response.setStatus(401);
                    return false;
                }

                request.setAttribute("currentUserNo", userNo);
                request.setAttribute("currentUserRole", role);

                boolean mustChange = mustChangePasswordCache.isRequired(role, userNo);
                boolean isWhitelisted = requestUri.endsWith("/update-password") || 
                                        requestUri.endsWith("/notice/list") || 
                                        requestUri.endsWith("/logout");

                if (mustChange && !isWhitelisted) {
                    securityLog.warn("【强制改密拦截】用户 {} 未修改初始密码，被拦截访问。URI: {}, IP: {}", userNo, requestUri, clientIp);
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"msg\":\"请先修改初始密码！\",\"data\":null}");
                    return false;
                }

                return true; 
                
            } catch (Exception e) {
                securityLog.error("【Token 解析失败】URI: {}, IP: {}, 错误: {}", requestUri, clientIp, e.getMessage());
                response.setStatus(401);
                return false;
            }
        }
        
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
                securityLog.warn("【非法票据】试图使用无效或已过期的 Ticket 访问接口。URI: {}, IP: {}", requestUri, clientIp);
            }
        }
        
        response.setStatus(401);
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}