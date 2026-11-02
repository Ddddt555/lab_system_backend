package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@TableName("major")
public class Major {
    @TableId("major_no")
    private String majorNo;

    @NotBlank(message = "专业名称不能为空！")
    private String majorName;
    @TableLogic
    private Integer deleted;

    @NotBlank(message = "所属院系不能为空！")
    private String deptNo;
    
    public String getMajorNo() { return majorNo; }
    public void setMajorNo(String majorNo) { this.majorNo = majorNo; }
    public String getMajorName() { return majorName; }
    public void setMajorName(String majorName) { this.majorName = majorName; }
    public String getDeptNo() { return deptNo; }
    public void setDeptNo(String deptNo) { this.deptNo = deptNo; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}