package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@TableName("lab_equipment")
public class LabEquipment {
    @TableId("equip_no")
    private String equipNo;

    @NotBlank(message = "设备名称不能为空！")
    private String equipName;

    private String model;

    @NotNull(message = "数量不能为空！")
    @Min(value = 1, message = "数量必须大于 0！")
    private Integer quantity;

    private String roomNo;

    @TableLogic
    private Integer deleted;
    
    public String getEquipNo() { return equipNo; }
    public void setEquipNo(String equipNo) { this.equipNo = equipNo; }
    public String getEquipName() { return equipName; }
    public void setEquipName(String equipName) { this.equipName = equipName; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}