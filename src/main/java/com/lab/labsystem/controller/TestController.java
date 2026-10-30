package com.lab.labsystem.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@RestController
public class TestController {

    @GetMapping("/getPwd")
    public String getPwd() {
        // 每次访问都会生成一个全新的、60位的密码哈希
        return new BCryptPasswordEncoder().encode("123456");
    }
}