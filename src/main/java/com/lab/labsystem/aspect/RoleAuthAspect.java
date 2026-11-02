package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j; // 🛑 新增
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

@Slf4j // 🛑 开启日志
@Aspect
@Component
public class RoleAuthAspect {

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

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        RequireRole requireRole = method.getAnnotation(RequireRole.class);
        
        if (requireRole == null) {
            requireRole = joinPoint.getTarget().getClass().getAnnotation(RequireRole.class);
        }

        if (requireRole != null) {
            boolean hasPermission = Arrays.asList(requireRole.value()).contains(currentRole);
            if (!hasPermission) {
                // 🛑 将 System.err 替换为 log.warn
                log.warn("🚫 拦截越权访问: 用户角色={}, 尝试访问={}", currentRole, request.getRequestURI());
                return Result.error("非法操作：您无权执行此操作！");
            }
        }

        return joinPoint.proceed();
    }
}