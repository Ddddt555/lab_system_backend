package com.lab.labsystem.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AccessLogInterceptor.class);

    @Autowired
    private MeterRegistry meterRegistry;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录请求开始时间
        request.setAttribute("startTime", System.currentTimeMillis());

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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 加上 try-catch，避免监控系统故障导致业务接口报 500
        try {
            Object startTimeObj = request.getAttribute("startTime");
            if (startTimeObj != null) {
                long startTime = (Long) startTimeObj;
                long costMs = System.currentTimeMillis() - startTime;
                
                String uri = request.getRequestURI();
                String module = extractModule(uri);

                // 记录到 Prometheus (Micrometer)
                Timer.builder("api.module.duration")
                        .description("API request duration grouped by module")
                        .tag("module", module)
                        .tag("method", request.getMethod())
                        .tag("status", String.valueOf(response.getStatus()))
                        .register(meterRegistry)
                        .record(costMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.warn("记录接口耗时监控指标失败，已忽略: {}", e.getMessage());
        }
    }

    /**
     * 从 URI 中提取业务模块名
     */
    private String extractModule(String uri) {
        if (uri == null || !uri.startsWith("/api/")) {
            return "other";
        }
        String[] parts = uri.split("/");
        if (parts.length > 2) {
            return parts[2];
        }
        return "unknown";
    }
}