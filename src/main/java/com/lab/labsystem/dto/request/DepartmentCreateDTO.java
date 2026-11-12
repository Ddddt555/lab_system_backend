package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DepartmentCreateDTO {
    @NotBlank(message = "院系名称不能为空")
    private String deptName;
}