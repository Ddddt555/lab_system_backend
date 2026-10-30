package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("exp_session")
public class ExpSession {
    @TableId("session_no")
    private String sessionNo; // 场次号
    private String projectNo; // 所属项目号
    private String roomNo;    // 所在实验室号
    private String workNo;    // 指导教师工号
    
    // 教务规范化时间字段
    private String semester;  // 学期
    private Integer weekNum;  // 周次
    private Integer dayOfWeek;// 星期几
    private String period;    // 节次
    
    // 绝对时间（由上述四个字段计算得出，用于排序和具体展示）
    private LocalDateTime sessionTime; 
    
    private Integer capacity; // 总容量
    private Integer remainingSeats; // 剩余机位数
    @TableLogic
    private Integer deleted;
    
    // 手动补齐 get/set
    public String getSessionNo() { return sessionNo; }
    public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }
    public String getProjectNo() { return projectNo; }
    public void setProjectNo(String projectNo) { this.projectNo = projectNo; }
    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
    public String getWorkNo() { return workNo; }
    public void setWorkNo(String workNo) { this.workNo = workNo; }
    
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
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}