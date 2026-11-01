package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.LogRecord;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.SysLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
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

    // 🛑 改用 Around 切面，这样即使目标方法抛出异常也能在 finally 中记录日志
    @Around("logPointCut()")
    public Object doAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            // 执行原方法
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            exception = e;
            throw e; // 异常继续往外抛，交给 GlobalExceptionHandler 处理
        } finally {
            // 无论成功失败，都在 finally 里记录日志
            saveLog(joinPoint, exception, System.currentTimeMillis() - startTime);
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, Exception exception, long costMs) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        LogRecord logRecord = signature.getMethod().getAnnotation(LogRecord.class);
        
        if (logRecord != null) {
            SysLog log = new SysLog();
            log.setAction(logRecord.value());
            log.setCreateTime(LocalDateTime.now());
            log.setCostMs(costMs);
            log.setSuccess(exception == null ? 1 : 0); // 1成功，0失败
            
            if (exception != null) {
                // 截取异常信息，防止超过数据库字段长度 (500)
                String msg = exception.getMessage();
                if (msg != null && msg.length() > 450) {
                    msg = msg.substring(0, 450) + "...";
                }
                log.setErrorMsg(msg);
            }

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // 🛑 抓取 IP（考虑反向代理的情况）
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                log.setIp(ip);
                log.setMethod(request.getMethod());
                log.setUri(request.getRequestURI());

                // 抓取身份
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
            }
            
            logMapper.insert(log);
        }
    }
}