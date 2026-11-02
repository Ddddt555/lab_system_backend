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
    public String getPwd() {
        // 🛑【安全警告】🛑
        // 这是一个极度危险的测试接口。
        // 它被故意放在 /api 前缀之外，所以不会被 JwtInterceptor 拦截。
        // 只能用于开发者本地测试 Bcrypt 加密结果。
        // 切勿在任何公网环境（如 cpolar 演示）中通过浏览器直接访问此路径！
    	
        return new BCryptPasswordEncoder().encode("123456");
    }
}