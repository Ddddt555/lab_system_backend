package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class SemesterCreateDTO {
    @NotBlank(message = "学期名称不能为空")
    private String semesterName;
    @NotNull(message = "开学日期不能为空")
    private LocalDate startDate;
    private Integer totalWeeks;
}