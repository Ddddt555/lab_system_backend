package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LabCreateDTO {
    @NotBlank(message = "实验室名称不能为空")
    private String roomName;
    private String location;
    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量必须大于 0")
    private Integer capacity;
}