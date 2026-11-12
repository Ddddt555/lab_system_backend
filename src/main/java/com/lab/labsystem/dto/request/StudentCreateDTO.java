package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StudentCreateDTO {
    @NotBlank(message = "学生姓名不能为空")
    private String name;

    @NotBlank(message = "所属行政班不能为空")
    private String adminClassNo;
}