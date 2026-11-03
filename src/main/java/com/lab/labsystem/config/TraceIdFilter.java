package com.lab.labsystem.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 🛑 全链路追踪过滤器
 * 作用：每次请求进来时生成唯一的 Trace ID，放入 MDC（映射诊断上下文），
 * 这样该请求后续产生的所有日志（包括 MyBatis SQL）都会自动带上这个 ID。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 必须最高优先级执行
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 1. 生成简短的 Trace ID（例如：a1b2c3d4）
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        
        // 2. 放入 MDC 和 Response Header（方便前端或 Nginx 排查）
        MDC.put(TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            // 3. 放行请求
            filterChain.doFilter(request, response);
        } finally {
            // 4. 🛑【极其重要】请求结束后必须清理 MDC，否则线程池复用会导致串数据！
            MDC.remove(TRACE_ID);
        }
    }
}