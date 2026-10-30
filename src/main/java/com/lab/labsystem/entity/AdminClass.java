package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("admin_class")
public class AdminClass {
    
    @TableId("admin_class_no")
    private String adminClassNo; // 纯数字拼接主键，如 24010203

    private String majorNo; // 所属专业号

    private Integer enrollmentYear; // 入学年份，如 2024

    private Integer classSeq; // 班级序号，如 1

    // ⚠️ 核心修改：注解和字段必须写在类里面，和上面的字段平级
    @TableLogic 
    private Integer deleted;

    // 手动补齐 get/set，防止 Lombok 报错
    public String getAdminClassNo() { return adminClassNo; }
    public void setAdminClassNo(String adminClassNo) { this.adminClassNo = adminClassNo; }

    public String getMajorNo() { return majorNo; }
    public void setMajorNo(String majorNo) { this.majorNo = majorNo; }

    public Integer getEnrollmentYear() { return enrollmentYear; }
    public void setEnrollmentYear(Integer enrollmentYear) { this.enrollmentYear = enrollmentYear; }

    public Integer getClassSeq() { return classSeq; }
    public void setClassSeq(Integer classSeq) { this.classSeq = classSeq; }
    
    // ⚠️ 别忘加 Get/Set 方法
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}