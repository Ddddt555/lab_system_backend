package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.LogRecord;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.SysLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
public class LogAspect {

    @Autowired
    private SysLogMapper logMapper;

    @Pointcut("@annotation(com.lab.labsystem.annotation.LogRecord)")
    public void logPointCut() {}

    @AfterReturning(pointcut = "logPointCut()", returning = "result")
    public void doAfterReturning(JoinPoint joinPoint, Object result) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        LogRecord logRecord = signature.getMethod().getAnnotation(LogRecord.class);
        
        if (logRecord != null) {
            SysLog log = new SysLog();
            log.setAction(logRecord.value());
            log.setCreateTime(LocalDateTime.now());
            
            // ⚠️ 核心修复：直接从 Request 上下文获取 JWT 拦截器放入的真实身份
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String role = (String) request.getAttribute("currentUserRole");
                String userNo = (String) request.getAttribute("currentUserNo");

                if ("admin".equals(role)) {
                    log.setOperator("管理员-" + (userNo == null ? "admin" : userNo));
                } else if ("teacher".equals(role)) {
                    log.setOperator("教师-" + userNo);
                } else if ("student".equals(role)) {
                    log.setOperator("学生-" + userNo);
                } else {
                    log.setOperator("未知身份");
                }
            } else {
                // 兜底逻辑：以防万一拿不到上下文
                log.setOperator("系统自动记录");
            }
            
            logMapper.insert(log);
        }
    }
}