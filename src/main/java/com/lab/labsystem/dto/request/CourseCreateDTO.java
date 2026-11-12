package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CourseCreateDTO {
    @NotBlank(message = "课程名不能为空")
    private String courseName;
    private Double credit;
    private Integer hours;
    @NotBlank(message = "开课学期不能为空")
    private String semester;
}