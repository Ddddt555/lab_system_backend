package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MajorCreateDTO {
    @NotBlank(message = "专业名称不能为空")
    private String majorName;
    @NotBlank(message = "所属院系不能为空")
    private String deptNo;
}