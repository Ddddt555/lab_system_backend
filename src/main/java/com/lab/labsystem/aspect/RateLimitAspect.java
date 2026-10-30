package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.RateLimit;
import com.lab.labsystem.common.Result;
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

    // 注入 Redisson 客户端
    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. 获取当前请求和用户身份
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        String userNo = (String) request.getAttribute("currentUserNo");
        
        // 如果拿不到用户ID（比如放行的接口），就用 IP 兜底
        if (userNo == null) userNo = request.getRemoteAddr();

        // 2. 获取方法名和 QPS
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getMethod().getName();
        double qps = rateLimit.qps();

        // 3. 构建唯一的限流 Key (加上前缀规范)
        String key = "rate_limit:" + methodName + ":" + userNo;

        // 4. 获取 RRateLimiter (Redisson 的分布式限流器)
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        // 5. 初始化限流器配置
        // RateType.OVERALL 表示所有实例共享这个令牌桶
        // trySetRate 是幂等的，如果该 key 已经存在配置，则不会覆盖
        rateLimiter.trySetRate(RateType.OVERALL, (long) qps, 1, RateIntervalUnit.SECONDS);

        // 6. 尝试获取令牌（不等待，拿不到直接拒绝）
        if (!rateLimiter.tryAcquire()) {
            return Result.error("手速太快啦！系统正在努力处理，请稍后再试~");
        }

        // 7. 令牌获取成功，放行执行原方法
        return joinPoint.proceed();
    }
}