package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@TableName("admin_class")
public class AdminClass {
    
    @TableId("admin_class_no")
    private String adminClassNo;

    @NotBlank(message = "所属专业不能为空！")
    private String majorNo;

    @NotNull(message = "入学年份不能为空！")
    private Integer enrollmentYear;

    @NotNull(message = "班级序号不能为空！")
    private Integer classSeq;

    @TableLogic 
    private Integer deleted;

    public String getAdminClassNo() { return adminClassNo; }
    public void setAdminClassNo(String adminClassNo) { this.adminClassNo = adminClassNo; }
    public String getMajorNo() { return majorNo; }
    public void setMajorNo(String majorNo) { this.majorNo = majorNo; }
    public Integer getEnrollmentYear() { return enrollmentYear; }
    public void setEnrollmentYear(Integer enrollmentYear) { this.enrollmentYear = enrollmentYear; }
    public Integer getClassSeq() { return classSeq; }
    public void setClassSeq(Integer classSeq) { this.classSeq = classSeq; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}