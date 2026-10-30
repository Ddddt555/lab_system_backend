package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("exp_project")
public class ExpProject {
    @TableId("project_no")
    private String projectNo; // 项目号
    private String projectName;
    
    // ⚠️ 改为教学班号
    private String classNo; 
    
    private Integer hours; // 学时
    private String guideFile; // 指导书路径
    private String standard; // 考核标准
    
    @TableLogic
    private Integer deleted;
    
    public String getProjectNo() { return projectNo; }
    public void setProjectNo(String projectNo) { this.projectNo = projectNo; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    
    // ⚠️ 修改这里
    public String getClassNo() { return classNo; }
    public void setClassNo(String classNo) { this.classNo = classNo; }
    
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public String getGuideFile() { return guideFile; }
    public void setGuideFile(String guideFile) { this.guideFile = guideFile; }
    public String getStandard() { return standard; }
    public void setStandard(String standard) { this.standard = standard; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}