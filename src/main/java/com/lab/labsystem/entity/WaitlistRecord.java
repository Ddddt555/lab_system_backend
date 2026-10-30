package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 候补排队记录。
 *
 * ⚠️ 注意：本表采用"物理删除"，不再使用 @TableLogic。
 *   原因：候补是瞬时排队状态，删除即表示离队；逻辑删除会让
 *         (session_no, student_no) 唯一键永远被占用，导致学生无法再次候补同一场次。
 *   排序权威来源在 Redis List（waitlist:queue:{sessionNo}），
 *   本表仅保存"当前在队人员快照"用于业务核对/兜底。
 */
@Data
@TableName("waitlist_record")
public class WaitlistRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionNo;
    private String studentNo;
    private LocalDateTime createTime;

    // 手动补全 Get/Set 防止 Lombok 抽风（与项目现有风格一致）
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionNo() { return sessionNo; }
    public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}