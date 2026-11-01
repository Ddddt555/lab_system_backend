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
                System.err.println("⚠️ 拦截越权访问: 用户角色=" + currentRole + ", 尝试访问=" + request.getRequestURI());
                // 🛑【核心修复】统一提示语，包含“无权”以匹配测试断言
                return Result.error("非法操作：您无权执行此操作！");
            }
        }

        return joinPoint.proceed();
    }
}