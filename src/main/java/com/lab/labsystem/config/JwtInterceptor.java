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
            // ⚠️ 2. 如果 Header 没有，尝试从 URL 参数获取（专为 window.open 准备，兼容 cpolar）
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
        
        // =========================================================
        // ⚠️ 3. 安全规范轨：尝试解析短时效一次性票据 (Ticket)
        // =========================================================
        // 🛑【警告：不要删除此段逻辑】🛑
        // 在非 cpolar 环境下，文件下载是通过 URL 携带 ticket 实现的。
        // 如果没有这段逻辑，所有生产环境的文件下载都会被拦截并返回 401！
        String ticket = request.getParameter("ticket");
        if (ticket != null && !ticket.isEmpty()) {
            String redisKey = "report:ticket:" + ticket;
            String userInfo = stringRedisTemplate.opsForValue().get(redisKey);
            if (userInfo != null) {
                // 用后即焚，防止重放攻击
                stringRedisTemplate.delete(redisKey);
                String[] parts = userInfo.split(":");
                request.setAttribute("currentUserNo", parts[0]);
                request.setAttribute("currentUserRole", parts[1]);
                
                // 🛑【核心修复】把 ticket 里绑定的文件名取出来，放到 request 里，供 Controller 校验
                if (parts.length > 2) {
                    request.setAttribute("ticketFileName", parts[2]);
                }
                
                return true;
            }
        }
        
        response.setStatus(401);
        return false;
    }
}