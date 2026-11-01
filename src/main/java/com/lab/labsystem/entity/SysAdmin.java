package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime; // ⚠️ 新增导入

@Data
@TableName("sys_admin")
public class SysAdmin {
    @TableId
    private String username;
    private String password;
    @TableLogic
    private Integer deleted;

    // 🛑【新增字段】是否强制修改密码
    private Integer mustChangePassword;
    
    // 🛑【新增字段】密码最后更新时间
    private LocalDateTime pwdUpdateTime;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }

    // 🛑 新字段的 Get 和 Set
    public Integer getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Integer mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public LocalDateTime getPwdUpdateTime() { return pwdUpdateTime; }
    public void setPwdUpdateTime(LocalDateTime pwdUpdateTime) { this.pwdUpdateTime = pwdUpdateTime; }
}