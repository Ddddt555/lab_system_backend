package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("title_dict")
public class TitleDict {
    @TableId("title_no")
    private String titleNo;
    private String titleName;
    @TableLogic
    private Integer deleted;

    public String getTitleNo() { return titleNo; }
    public void setTitleNo(String titleNo) { this.titleNo = titleNo; }
    public String getTitleName() { return titleName; }
    public void setTitleName(String titleName) { this.titleName = titleName; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}