package com.lab.labsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // ⚠️ 新增导包

@SpringBootApplication
@EnableScheduling // ⚠️ 核心新增：开启定时任务支持
public class LabsystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabsystemApplication.class, args);
    }
}