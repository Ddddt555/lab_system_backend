package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeacherCreateDTO {
    @NotBlank(message = "教师姓名不能为空")
    private String name;
    private String deptNo;
    private String titleNo;
}