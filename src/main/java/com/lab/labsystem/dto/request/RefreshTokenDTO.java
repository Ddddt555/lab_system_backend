package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 Token 请求参数对象。
 */
@Data
public class RefreshTokenDTO {

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;

    /** 浏览器设备指纹 */
    private String deviceId;
}