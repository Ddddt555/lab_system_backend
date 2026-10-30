package com.lab.labsystem.annotation;

import java.lang.annotation.*;

/**
 * 自定义鉴权注解：用于校验当前用户的角色
 * 可以打在类上（对该类所有方法生效），也可以打在方法上。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /**
     * 允许访问的角色列表，例如：{"teacher", "admin"}
     */
    String[] value();
}