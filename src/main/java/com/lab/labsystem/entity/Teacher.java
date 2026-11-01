package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime; // ⚠️ 新增导入

@Data
@TableName("teacher")
public class Teacher {
    @TableId("work_no")
    private String workNo;
    private String name;
    private String password;
    
    // ⚠️ 修改：指向院系表和职称表的外键
    private String deptNo;
    private String titleNo;
    @TableLogic
    private Integer deleted;

    // 🛑【新增字段】是否强制修改密码
    private Integer mustChangePassword;
    
    // 🛑【新增字段】密码最后更新时间
    private LocalDateTime pwdUpdateTime;
    
    public String getWorkNo() { return workNo; }
    public void setWorkNo(String workNo) { this.workNo = workNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getDeptNo() { return deptNo; }
    public void setDeptNo(String deptNo) { this.deptNo = deptNo; }
    public String getTitleNo() { return titleNo; }
    public void setTitleNo(String titleNo) { this.titleNo = titleNo; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    // 🛑 新字段的 Get 和 Set
    public Integer getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Integer mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public LocalDateTime getPwdUpdateTime() { return pwdUpdateTime; }
    public void setPwdUpdateTime(LocalDateTime pwdUpdateTime) { this.pwdUpdateTime = pwdUpdateTime; }
}