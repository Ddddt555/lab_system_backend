package com.lab.labsystem.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EquipmentCreateDTO {
    @NotBlank(message = "设备名称不能为空")
    private String equipName;
    private String model;
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    private Integer quantity;
    private String roomNo;
}