package com.lab.labsystem.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// 🛑【严重警告：此控制器仅在本地 dev 环境下运行】🛑
// 原因：/getPwd 接口可以无限次生成密码密文，且没有经过 API 拦截器。
// 如果暴露到公网，恶意攻击者可以借此接口进行攻击探测。
// 通过 @Profile("!prod")，确保在正式生产环境（profile=prod）下，此类绝对不会被 Spring 加载。
@Profile("!prod")
@RestController
public class TestController {

    @GetMapping("/getPwd")
    public org.springframework.http.ResponseEntity<String> getPwd(
            jakarta.servlet.http.HttpServletRequest request) {
        // 【安全加固】仅允许本机回环地址调用。
        // 该接口不经过 JwtInterceptor，一旦在非生产 profile 的公网环境（如 cpolar 演示）
        // 暴露，任何人都可以无限次调用。这里直接返回 404，不抛异常
        // （GlobalExceptionHandler 会兜底捕获异常并转成 200，掩盖真实状态码）。
        String remoteAddr = request.getRemoteAddr();
        boolean isLoopback = "127.0.0.1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr);
        if (!isLoopback) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        return org.springframework.http.ResponseEntity.ok(
                new BCryptPasswordEncoder().encode("123456"));
    }
}