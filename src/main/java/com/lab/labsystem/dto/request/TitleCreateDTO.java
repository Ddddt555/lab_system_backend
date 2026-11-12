package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TitleCreateDTO {
    @NotBlank(message = "职称名称不能为空")
    private String titleName;
}