package com.lab.labsystem.aspect;

import com.lab.labsystem.annotation.RateLimit;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
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
        // 使用全限定类名（包含包名），避免不同包下同名 Controller 的冲突
        String className = signature.getDeclaringType().getName();
        double qps = rateLimit.qps();

        // 拦截非法的 QPS 配置
        if (qps <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR.getCode(), "接口限流配置非法：QPS 必须大于 0");
        }

        long rate;
        long interval;
        
        // 处理精度问题：为了兼容小数的 QPS，采用放大时间窗口的方式
        if (qps >= 1) {
            // 例如 qps = 1.5，则转为 15 次 / 10 秒
            rate = Math.round(qps * 10);
            interval = 10;
        } else {
            // 例如 qps = 0.5，则转为 1 次 / 2 秒
            rate = 1;
            interval = (long) Math.ceil(1.0 / qps);
        }

        // 将 qps 的值拼入 Key 中，一旦代码中配置变更，就会生成新 Key，强制应用新限流规则
        String key = "rate_limit:" + className + ":" + methodName + ":" + userNo + ":" + qps;

        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        
        // 由于 Key 已经包含了配置信息，这里的 trySetRate 只会对新 Key 生效，不会产生旧配置残留
        rateLimiter.trySetRate(RateType.OVERALL, rate, interval, RateIntervalUnit.SECONDS);

        if (!rateLimiter.tryAcquire()) {
            // 抛出业务异常，交由全局异常处理器转换为真实的 HTTP 429 状态码
            throw new BusinessException(429, "手速太快啦！系统正在努力处理，请稍后再试~");
        }

        return joinPoint.proceed();
    }
}