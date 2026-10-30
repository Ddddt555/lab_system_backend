package com.lab.labsystem.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

public class AccessLogInterceptor implements HandlerInterceptor {

    // ⚠️ 使用 SLF4J 记录，交给底层的 Logback 去管理写入和切割
    private static final Logger log = LoggerFactory.getLogger(AccessLogInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        String url = request.getRequestURI();
        String userAgent = request.getHeader("User-Agent");
        
        // 过滤静态资源和图标
        if (!url.startsWith("/assets") && !url.equals("/") && !url.contains("favicon") && !url.contains("/actuator")) {
            log.info("【访客来访】IP: {} | 路径: {} | 设备: {}", ip, url, userAgent);
        }
        
        return true;
    }
}