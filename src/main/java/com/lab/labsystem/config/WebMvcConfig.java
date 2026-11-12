package com.lab.labsystem.config;

import com.lab.labsystem.service.auth.MustChangePasswordCache;
import com.lab.labsystem.service.auth.TokenService;
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

    @Autowired
    private TokenService tokenService;

    @Autowired
    private MustChangePasswordCache mustChangePasswordCache;

    // 注入 AccessLogInterceptor
    @Autowired
    private AccessLogInterceptor accessLogInterceptor;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/student-home").setViewName("forward:/index.html");
        registry.addViewController("/teacher-home").setViewName("forward:/index.html");
        registry.addViewController("/admin-home").setViewName("forward:/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. JWT 鉴权拦截器
        registry.addInterceptor(new JwtInterceptor(tokenService, stringRedisTemplate, mustChangePasswordCache))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login/**")
                .excludePathPatterns("/api/captcha")
                .excludePathPatterns("/api/refresh-token");

        // 2. 访问日志与耗时统计拦截器
        registry.addInterceptor(accessLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/assets/**", "/favicon.ico", "/index.html", "/actuator/**");
    }
}