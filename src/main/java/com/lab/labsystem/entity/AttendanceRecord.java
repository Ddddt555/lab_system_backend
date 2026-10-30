package com.lab.labsystem.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("attendance_record")
public class AttendanceRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionNo;
    private String studentNo;
    private LocalDateTime signTime;
    private Integer status;

    // ⚠️ 手动补上 Get 和 Set 方法，彻底绕开 Lombok 报错
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionNo() { return sessionNo; }
    public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public LocalDateTime getSignTime() { return signTime; }
    public void setSignTime(LocalDateTime signTime) { this.signTime = signTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}