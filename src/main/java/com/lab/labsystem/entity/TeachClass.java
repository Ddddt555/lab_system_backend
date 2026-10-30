package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("teach_class")
public class TeachClass {
    
    @TableId("class_no")
    private String classNo;
    
    private String courseNo;
    
    private String workNo;
    
    private String semester;

    // ⚠️ 新增：平时成绩权重 (%)
    private Integer dailyWeight;

    // ⚠️ 新增：报告成绩权重 (%)
    private Integer reportWeight;
    @TableLogic
    private Integer deleted;

    // ⚠️ 手动补全 get 和 set，防止 Lombok 报错
    public String getClassNo() {
        return classNo;
    }

    public void setClassNo(String classNo) {
        this.classNo = classNo;
    }

    public String getCourseNo() {
        return courseNo;
    }

    public void setCourseNo(String courseNo) {
        this.courseNo = courseNo;
    }

    public String getWorkNo() {
        return workNo;
    }

    public void setWorkNo(String workNo) {
        this.workNo = workNo;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    // ⚠️ 新增字段的 Get/Set
    public Integer getDailyWeight() {
        return dailyWeight;
    }

    public void setDailyWeight(Integer dailyWeight) {
        this.dailyWeight = dailyWeight;
    }

    public Integer getReportWeight() {
        return reportWeight;
    }

    public void setReportWeight(Integer reportWeight) {
        this.reportWeight = reportWeight;
    }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}