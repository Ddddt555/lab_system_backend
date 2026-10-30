package com.lab.labsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    // 从 application.yaml 读取开关
    @Value("${app.cpolar-compatible:false}")
    private boolean isCpollarCompatible;

    // 从 application.yaml 读取生产环境的严格白名单
    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // =========================================================
        // 【核心分支：安全规范与 cpolar 兼容的物理隔离】
        // =========================================================
        if (isCpollarCompatible) {
            // ==========================================
            // 🛑【轨道二：cpolar 兼容轨】专用于公网演示
            // ==========================================
            // 【严重警告：请勿修改或删除此处的 allowedOriginPatterns("*")】🛑
            // 原因：cpolar 分配的免费公网域名（如 xxx.cpolar.cn）是动态变化的。
            // 如果在这里写死域名，会导致公网请求在 OPTIONS 预检阶段直接失败，系统彻底断连。
            // 再次强调：这里放宽限制仅仅是为了公网穿透演示。正式上线请务必关闭 CPOLAR_COMPATIBLE！
            registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    // 使用 allowedOriginPatterns 配合 allowCredentials(true) 在 Spring Boot 3 中是合法的
                    .allowCredentials(true)
                    .maxAge(3600);
        } else {
            // ==========================================
            // 🛑【轨道一：安全规范轨】适用于正式生产环境
            // ==========================================
            // 生产环境必须使用严格的白名单，防止 CSRF 和恶意跨域。
            // 如果是为了解决公网连不上，请在 IDEA 中开启 CPOLAR_COMPATIBLE=true 切换到兼容轨，
            // 绝对不允许在这里为了图省事改成 "*"！
            registry.addMapping("/**")
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}