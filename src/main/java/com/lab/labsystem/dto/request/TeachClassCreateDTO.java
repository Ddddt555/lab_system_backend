package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeachClassCreateDTO {
    @NotBlank(message = "所属课程不能为空")
    private String courseNo;
    @NotBlank(message = "授课教师不能为空")
    private String workNo;
    @NotBlank(message = "开课学期不能为空")
    private String semester;
}