package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SignRequestDTO {
    @NotBlank(message = "场次号不能为空")
    private String sessionNo;

    @NotBlank(message = "签到码不能为空")
    private String code;
}