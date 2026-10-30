package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Aspect
@Component
public class RoleAuthAspect {

    @Around("@annotation(requireRole) || @within(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        // JwtInterceptor 已经把角色放进去了
        String currentRole = (String) request.getAttribute("currentUserRole");

        // 1. 如果没有角色，说明没登录或者 Token 失效
        if (currentRole == null) {
            return Result.error("未登录或身份失效！");
        }

        // 2. 校验当前角色是否在允许的列表中
        boolean hasPermission = Arrays.asList(requireRole.value()).contains(currentRole);
        if (!hasPermission) {
            System.err.println("⚠️ 拦截越权访问: 用户角色=" + currentRole + ", 尝试访问=" + request.getRequestURI());
            return Result.error("非法操作：您没有权限执行此操作！");
        }

        // 3. 校验通过，放行
        return joinPoint.proceed();
    }
}