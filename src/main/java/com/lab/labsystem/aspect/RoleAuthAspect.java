package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class RoleAuthAspect {

    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Around("@annotation(com.lab.labsystem.annotation.RequireRole) || @within(com.lab.labsystem.annotation.RequireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        String currentRole = (String) request.getAttribute("currentUserRole");
        String currentUserNo = (String) request.getAttribute("currentUserNo");

        if (currentRole == null) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED);
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
                String clientIp = getClientIp(request);
                
                // 写入独立安全日志
                securityLog.warn("【越权拦截】用户: {}, 角色: {}, 尝试访问: {}, IP: {}", 
                        currentUserNo != null ? currentUserNo : "未知", 
                        currentRole, 
                        request.getRequestURI(), 
                        clientIp);
                        
                // 抛出业务异常，交由全局异常处理器转换为真实的 403 状态码
                throw BusinessException.of(ErrorCode.FORBIDDEN, "非法操作：您无权执行此操作！");
            }
        }

        return joinPoint.proceed();
    }

    /**
     * 获取请求的真实客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}