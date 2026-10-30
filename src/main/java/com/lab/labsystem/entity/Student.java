package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("student")
public class Student {

    @TableId("student_no") // 明确指定主键字段
    private String studentNo;

    @TableField("name")
    private String name;

    @TableField("password")
    private String password;

    // ⚠️ 修改：原先的 class_name 和 major 移除了，替换为行政班号
    @TableField("admin_class_no") 
    private String adminClassNo;
    @TableLogic
    private Integer deleted;
    
    // --- 保险起见，手动补上 Get 和 Set 方法 ---
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getAdminClassNo() { return adminClassNo; }
    public void setAdminClassNo(String adminClassNo) { this.adminClassNo = adminClassNo; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}