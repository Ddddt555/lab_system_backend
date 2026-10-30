package com.lab.labsystem.config;

import com.lab.labsystem.common.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

public class JwtInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public JwtInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // ⚠️ 1. 优先从 Header 获取 Token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        } else {
            // ⚠️ 2. 如果 Header 没有，尝试从 URL 参数获取（专为 window.open 准备）
            String paramToken = request.getParameter("token");
            if (paramToken != null && !paramToken.isEmpty()) {
                token = paramToken;
            }
        }

        if (token != null) {
            try {
                Claims claims = JwtUtil.parseToken(token);
                String jti = claims.getId();
                
                if (jti != null && Boolean.TRUE.equals(stringRedisTemplate.hasKey("jwt:blacklist:" + jti))) {
                    response.setStatus(401);
                    return false;
                }

                request.setAttribute("currentUserNo", claims.getSubject());
                request.setAttribute("currentUserRole", claims.get("role"));

                return true; 
                
            } catch (Exception e) {
                response.setStatus(401);
                return false;
            }
        }
        
        response.setStatus(401);
        return false;
    }
}