package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WeightRequestDTO {
    @NotBlank(message = "教学班号不能为空")
    private String classNo;

    @NotNull(message = "平时成绩权重不能为空")
    private Integer dailyWeight;

    @NotNull(message = "报告成绩权重不能为空")
    private Integer reportWeight;
}