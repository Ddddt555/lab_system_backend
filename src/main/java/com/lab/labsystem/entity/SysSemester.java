package com.lab.labsystem.entity;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
@Data
@TableName("sys_semester")
public class SysSemester {
    @TableId("semester_name")
    private String semesterName;
    private LocalDate startDate;
    private Integer totalWeeks;
    @TableLogic
    private Integer deleted;
    
    public String getSemesterName() { return semesterName; }
    public void setSemesterName(String semesterName) { this.semesterName = semesterName; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public Integer getTotalWeeks() { return totalWeeks; }
    public void setTotalWeeks(Integer totalWeeks) { this.totalWeeks = totalWeeks; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}