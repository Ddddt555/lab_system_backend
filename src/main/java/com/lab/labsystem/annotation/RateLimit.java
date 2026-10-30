package com.lab.labsystem.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    // 每秒生成的令牌数（即允许的 QPS）
    double qps() default 1.0;
}