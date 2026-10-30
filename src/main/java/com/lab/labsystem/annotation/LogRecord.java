package com.lab.labsystem.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD) // 这个注解只能打在方法上面
@Retention(RetentionPolicy.RUNTIME) // 运行时生效
@Documented
public @interface LogRecord {
    String value() default ""; // 用来记录具体做了什么操作，比如 "新增学生"
}