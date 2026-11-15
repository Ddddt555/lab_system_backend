package com.lab.labsystem.common;

import java.util.regex.Pattern;

/**
 * 设备指纹校验器。
 * 确保在登录签发、刷新 Token 以及访问拦截环节，对设备 ID 的格式要求绝对一致。
 */
public class DeviceIdValidator {

    // 允许字母、数字、下划线、短横线，长度限制在 16 到 64 位之间
    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{16,64}$");

    /**
     * 校验设备 ID 格式是否合法
     */
    public static boolean isValid(String deviceId) {
        return deviceId != null && PATTERN.matcher(deviceId).matches();
    }
}