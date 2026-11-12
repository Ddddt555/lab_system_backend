package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SessionCreateDTO {
    @NotBlank(message = "实验项目不能为空")
    private String projectNo;

    @NotBlank(message = "实验室不能为空")
    private String roomNo;

    @NotBlank(message = "学期不能为空")
    private String semester;

    @NotNull(message = "周次不能为空")
    private Integer weekNum;

    @NotNull(message = "星期不能为空")
    private Integer dayOfWeek;

    @NotBlank(message = "节次不能为空")
    private String period;

    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量至少为 1")
    private Integer capacity;
}