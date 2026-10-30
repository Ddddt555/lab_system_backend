package com.lab.labsystem.entity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
@Data
@TableName("student_teach_class")
public class StudentTeachClass {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String studentNo;
    private String classNo;
    private String semester;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getClassNo() { return classNo; }
    public void setClassNo(String classNo) { this.classNo = classNo; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
}