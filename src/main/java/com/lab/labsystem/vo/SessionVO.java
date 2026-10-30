package com.lab.labsystem.vo;

import java.time.LocalDateTime;

public class SessionVO {
    private String sessionNo;
    private String projectNo;
    private String workNo;
    private String roomNo;
    
    // 教务规范化时间字段
    private String semester;
    private Integer weekNum;
    private Integer dayOfWeek;
    private String period;

    private LocalDateTime sessionTime;
    private Integer capacity;
    private Integer remainingSeats;
    
    private String bookingNo;
    private String teacherName;
    private String projectName;
    private String guideFile;
    
    private Double score;
    private String comment;

    // ⚠️ 补上这两个成绩管理用的字段
    private String studentNo; 
    private Boolean isGraded; 
    private Double reportScore; // 报告均分
    private Double totalScore;  // 总评成绩

    // --- 手动补齐所有 get 和 set 方法 ---
    public String getSessionNo() { return sessionNo; }
    public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }
    public String getProjectNo() { return projectNo; }
    public void setProjectNo(String projectNo) { this.projectNo = projectNo; }
    public String getWorkNo() { return workNo; }
    public void setWorkNo(String workNo) { this.workNo = workNo; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Integer getWeekNum() { return weekNum; }
    public void setWeekNum(Integer weekNum) { this.weekNum = weekNum; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public LocalDateTime getSessionTime() { return sessionTime; }
    public void setSessionTime(LocalDateTime sessionTime) { this.sessionTime = sessionTime; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getRemainingSeats() { return remainingSeats; }
    public void setRemainingSeats(Integer remainingSeats) { this.remainingSeats = remainingSeats; }

    public String getBookingNo() { return bookingNo; }
    public void setBookingNo(String bookingNo) { this.bookingNo = bookingNo; }
    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String teacherName) { this.teacherName = teacherName; }
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getGuideFile() { return guideFile; }
    public void setGuideFile(String guideFile) { this.guideFile = guideFile; }
    
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    // ⚠️ 最新加上的 get/set
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    
    public Boolean getIsGraded() { return isGraded; }
    public void setIsGraded(Boolean isGraded) { this.isGraded = isGraded; }
    
    public Double getReportScore() { return reportScore; }
    public void setReportScore(Double reportScore) { this.reportScore = reportScore; }
    
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
}