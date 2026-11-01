package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime; // ⚠️ 新增导入

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

    // 🛑【新增字段】是否强制修改密码
    private Integer mustChangePassword;
    
    // 🛑【新增字段】密码最后更新时间
    private LocalDateTime pwdUpdateTime;
    
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

    // 🛑 新字段的 Get 和 Set
    public Integer getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Integer mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public LocalDateTime getPwdUpdateTime() { return pwdUpdateTime; }
    public void setPwdUpdateTime(LocalDateTime pwdUpdateTime) { this.pwdUpdateTime = pwdUpdateTime; }
}