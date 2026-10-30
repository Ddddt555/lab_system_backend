package com.lab.labsystem.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StudentVO {
    
    // 学生基本信息
    private String studentNo; // 学号
    private String name;      // 姓名
    private String className; // 班级
    private String major;     // 专业
    
    // 预约记录特有的信息
    private LocalDateTime bookingTime; // 预约时间

    // --- 防止 Lombok 罢工，手动补齐 get 和 set ---
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getMajor() { return major; }
    public void setMajor(String major) { this.major = major; }

    public LocalDateTime getBookingTime() { return bookingTime; }
    public void setBookingTime(LocalDateTime bookingTime) { this.bookingTime = bookingTime; }
}