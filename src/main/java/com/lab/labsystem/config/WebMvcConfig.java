package com.lab.labsystem.config;

import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.TeacherMapper;
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
    
    // 🛑 注入三个 Mapper，供拦截器查库使用
    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private TeacherMapper teacherMapper;
    @Autowired
    private SysAdminMapper sysAdminMapper;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/student-home").setViewName("forward:/index.html");
        registry.addViewController("/teacher-home").setViewName("forward:/index.html");
        registry.addViewController("/admin-home").setViewName("forward:/index.html");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. JWT 鉴权拦截器
        registry.addInterceptor(new JwtInterceptor(stringRedisTemplate, studentMapper, teacherMapper, sysAdminMapper))
                .addPathPatterns("/api/**") // ⚠️ 拦截所有 API
                .excludePathPatterns("/api/login/**")
                .excludePathPatterns("/api/captcha");
                
        // 2. 访问日志拦截器
        registry.addInterceptor(new AccessLogInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/assets/**", "/favicon.ico", "/index.html", "/actuator/**");
    }
}