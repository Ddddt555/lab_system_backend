package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.RateLimit;
import com.lab.labsystem.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        String userNo = (String) request.getAttribute("currentUserNo");
        
        // 如果拿不到用户ID（比如放行的接口），就用 IP 兜底
        if (userNo == null) userNo = request.getRemoteAddr();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        String className = signature.getDeclaringType().getSimpleName();
        double qps = rateLimit.qps();

        // 将类名加入 Key 中，防止不同 Controller 的同名方法共享限流器
        String key = "rate_limit:" + className + ":" + methodName + ":" + userNo;

        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        long rate = (long) qps;
        long interval = 1;

        // 处理 qps 小于 1 的场景，例如 0.5 转换为 1次/2秒，防止 (long) 强转后变成 0 导致限流异常
        if (rate < 1) {
            rate = 1;
            interval = (long) Math.ceil(1.0 / qps);
        }

        // RateType.OVERALL 表示所有实例共享这个令牌桶
        rateLimiter.trySetRate(RateType.OVERALL, rate, interval, RateIntervalUnit.SECONDS);

        if (!rateLimiter.tryAcquire()) {
            // 抛出业务异常，交由全局异常处理器转换为真实的 HTTP 429 状态码
            throw new BusinessException(429, "手速太快啦！系统正在努力处理，请稍后再试~");
        }

        return joinPoint.proceed();
    }
}