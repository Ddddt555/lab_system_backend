package com.lab.labsystem.common;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CaptchaService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // Redis 中 Key 的前缀规范
    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    // 验证码有效期：5 分钟
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    // 生成验证码，返回 UUID 和 Base64 图片
    public Map<String, String> generateCaptcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String code = captcha.getCode(); // 获取验证码文本
        String base64Image = captcha.getImageBase64(); // 获取图片Base64

        String uuid = UUID.randomUUID().toString();
        
        // ⚠️ 存入 Redis，并设置 5 分钟自动过期
        stringRedisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + uuid, code, CAPTCHA_TTL);

        Map<String, String> result = new ConcurrentHashMap<>();
        result.put("captchaKey", uuid);
        result.put("captchaImage", "data:image/png;base64," + base64Image);
        return result;
    }

    // 校验验证码
    public boolean verifyCaptcha(String uuid, String userInputCode) {
        if (uuid == null || userInputCode == null) return false;
        
        String redisKey = CAPTCHA_KEY_PREFIX + uuid;
        String cachedCode = stringRedisTemplate.opsForValue().get(redisKey);
        
        // ⚠️ 无论对错，校验后立刻删除，防止被暴力重放
        stringRedisTemplate.delete(redisKey);

        // 如果 Redis 里没数据（过期或伪造的Key），直接 false
        if (cachedCode == null) {
            return false;
        }

        // 忽略大小写比对
        return cachedCode.equalsIgnoreCase(userInputCode);
    }
}