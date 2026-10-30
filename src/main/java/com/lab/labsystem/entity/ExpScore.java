package com.lab.labsystem.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("exp_score")
public class ExpScore {
    @TableId("score_no")
    private String scoreNo;
    private String studentNo;
    
    // ⚠️ 改为教学班号
    private String classNo; 
    
    private Double dailyScore;
    private Double reportScore;
    private Double totalScore;
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;

    public String getScoreNo() { return scoreNo; }
    public void setScoreNo(String scoreNo) { this.scoreNo = scoreNo; }
    
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    
    // ⚠️ 修改这里，把 CourseNo 换成 ClassNo
    public String getClassNo() { return classNo; }
    public void setClassNo(String classNo) { this.classNo = classNo; }
    
    public Double getDailyScore() { return dailyScore; }
    public void setDailyScore(Double dailyScore) { this.dailyScore = dailyScore; }
    
    public Double getReportScore() { return reportScore; }
    public void setReportScore(Double reportScore) { this.reportScore = reportScore; }
    
    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }
    
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}