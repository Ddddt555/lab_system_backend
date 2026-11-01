package com.lab.labsystem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cpolar-compatible:false}")
    private boolean isCpollarCompatible;

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (isCpollarCompatible) {
            // 🛑 cpolar 公网演示模式：允许动态域名穿透
            registry.addMapping("/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        } else {
            // 🛑 安全规范轨：生产环境严格白名单
            // 【核心防呆】如果白名单为空，或者包含了 "*"，直接抛异常阻止系统启动！
            if (allowedOrigins == null || allowedOrigins.length == 0 || 
                Arrays.asList(allowedOrigins).contains("*")) {
                throw new IllegalArgumentException(
                    "【生产环境安全拒绝】CORS 跨域白名单未配置或配置了 '*'，请检查环境变量 CORS_ORIGINS！"
                );
            }

            registry.addMapping("/**")
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}