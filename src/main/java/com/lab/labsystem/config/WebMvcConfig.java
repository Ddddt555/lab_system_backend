package com.lab.labsystem.config;

import com.lab.labsystem.service.MustChangePasswordCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 强制改密标记的缓存（替代原先在拦截器里直接查三张表）
    @Autowired
    private MustChangePasswordCache mustChangePasswordCache;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/student-home").setViewName("forward:/index.html");
        registry.addViewController("/teacher-home").setViewName("forward:/index.html");
        registry.addViewController("/admin-home").setViewName("forward:/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. JWT 鉴权拦截器
        registry.addInterceptor(new JwtInterceptor(stringRedisTemplate, mustChangePasswordCache))
                .addPathPatterns("/api/**") // ⚠️ 拦截所有 API
                .excludePathPatterns("/api/login/**")
                .excludePathPatterns("/api/captcha")
                .excludePathPatterns("/api/refresh-token"); // 🛑【核心修复】放行无感刷新接口
                
        // 2. 访问日志拦截器
        registry.addInterceptor(new AccessLogInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/assets/**", "/favicon.ico", "/index.html", "/actuator/**");
    }
}