package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank; // 🛑 新增
import lombok.Data;

@Data
@TableName("exp_course")
public class ExpCourse {
    @TableId("course_no")
    private String courseNo; 

    @NotBlank(message = "课程名不能为空！")
    private String courseName; 

    private Double credit; 
    private Integer hours; 

    @NotBlank(message = "开课学期不能为空！")
    private String semester; 

    @TableLogic
    private Integer deleted;

    public String getCourseNo() { return courseNo; }
    public void setCourseNo(String courseNo) { this.courseNo = courseNo; }
    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }
    public Double getCredit() { return credit; }
    public void setCredit(Double credit) { this.credit = credit; }
    public Integer getHours() { return hours; }
    public void setHours(Integer hours) { this.hours = hours; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}