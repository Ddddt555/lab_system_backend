package com.lab.labsystem.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class StudentImportDTO {
    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("行政班号")
    private String adminClassNo;

    // ⚠️ 手动补全 get 和 set，防止 Lombok 抽风
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdminClassNo() {
        return adminClassNo;
    }

    public void setAdminClassNo(String adminClassNo) {
        this.adminClassNo = adminClassNo;
    }
}