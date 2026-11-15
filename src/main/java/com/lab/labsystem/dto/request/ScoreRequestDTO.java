package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScoreRequestDTO {
    @NotBlank(message = "学号不能为空")
    private String studentNo;

    @NotBlank(message = "教学班号不能为空")
    private String classNo;

    @NotNull(message = "平时成绩不能为空")
    private Double dailyScore;
}