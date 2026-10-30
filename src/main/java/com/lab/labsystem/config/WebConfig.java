package com.lab.labsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ⚠️ 已移除 /uploads/** 的公开静态映射。
 * 所有文件访问统一走：
 *   - /api/report/download/{fileName}  （需登录 + 对象级权限）
 *   - /api/report/preview/{fileName}   （需登录 + 对象级权限）
 * 以避免"知道文件名就能下载任意文件"的越权风险。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // 故意留空，保留类以兼容现有引用
}