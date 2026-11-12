package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectCreateDTO {
    @NotBlank(message = "项目名称不能为空")
    private String projectName;

    @NotBlank(message = "所属教学班不能为空")
    private String classNo;

    @NotNull(message = "学时不能为空")
    @Min(value = 1, message = "学时至少为 1")
    private Integer hours;

    private String standard;
}