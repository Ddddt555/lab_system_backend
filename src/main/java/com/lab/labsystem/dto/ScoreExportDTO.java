package com.lab.labsystem.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
@ColumnWidth(20)
public class ScoreExportDTO {
    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("姓名")
    private String studentName;

    @ExcelProperty("所属行政班")
    private String adminClassName;

    @ExcelProperty("平时成绩(30%)")
    private Double dailyScore;

    @ExcelProperty("报告均分(70%)")
    private Double reportScore;

    @ExcelProperty("总评成绩")
    private Double totalScore;

    // ⚠️ 手动补全 get 和 set 方法，防 Lombok 报错
    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getAdminClassName() {
        return adminClassName;
    }

    public void setAdminClassName(String adminClassName) {
        this.adminClassName = adminClassName;
    }

    public Double getDailyScore() {
        return dailyScore;
    }

    public void setDailyScore(Double dailyScore) {
        this.dailyScore = dailyScore;
    }

    public Double getReportScore() {
        return reportScore;
    }

    public void setReportScore(Double reportScore) {
        this.reportScore = reportScore;
    }

    public Double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Double totalScore) {
        this.totalScore = totalScore;
    }
}