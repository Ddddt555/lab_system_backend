package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class RoleAuthAspect {

    // 🛑 注意：这里移除了方法参数中的 RequireRole requireRole
    @Around("@annotation(com.lab.labsystem.annotation.RequireRole) || @within(com.lab.labsystem.annotation.RequireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        String currentRole = (String) request.getAttribute("currentUserRole");

        if (currentRole == null) {
            return Result.error("未登录或身份失效！");
        }

        // 🛑【核心修复】手动通过反射获取方法或类上的注解，避免依赖参数注入
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 优先找方法上的注解
        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        
        // 如果方法上没有，找类上的注解
        if (requireRole == null) {
            requireRole = joinPoint.getTarget().getClass().getAnnotation(RequireRole.class);
        }

        if (requireRole != null) {
            boolean hasPermission = Arrays.asList(requireRole.value()).contains(currentRole);
            if (!hasPermission) {
                System.err.println("⚠️ 拦截越权访问: 用户角色=" + currentRole + ", 尝试访问=" + request.getRequestURI());
                return Result.error("非法操作：您没有权限执行此操作！");
            }
        }

        return joinPoint.proceed();
    }
}