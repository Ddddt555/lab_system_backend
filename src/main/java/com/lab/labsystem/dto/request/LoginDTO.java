package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 登录请求参数对象。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "验证码标识不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;

    /** 浏览器设备指纹，用于 Token 绑定 */
    @NotBlank(message = "设备标识不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9_-]{16,64}$", message = "设备标识格式非法")
    private String deviceId;
}