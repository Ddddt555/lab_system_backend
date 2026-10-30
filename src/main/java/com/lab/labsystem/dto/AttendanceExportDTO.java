package com.lab.labsystem.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;

@ColumnWidth(20)
public class AttendanceExportDTO {

    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("行政班") // 纯数字
    private String classNo;

    @ExcelProperty("签到状态")
    private String status; // 已签到 / 缺勤

    @ExcelProperty("签到时间")
    private String signTime;

    // ⚠️ 手动补齐 Get 和 Set
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getClassNo() { return classNo; }
    public void setClassNo(String classNo) { this.classNo = classNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSignTime() { return signTime; }
    public void setSignTime(String signTime) { this.signTime = signTime; }
}