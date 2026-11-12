package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminClassCreateDTO {
    @NotBlank(message = "所属专业不能为空")
    private String majorNo;
    @NotNull(message = "入学年份不能为空")
    private Integer enrollmentYear;
    @NotNull(message = "班级序号不能为空")
    private Integer classSeq;
}