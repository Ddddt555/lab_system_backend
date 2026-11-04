package com.lab.labsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_notice_read")
public class SysNoticeRead {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String noticeId;
    private String userNo;
    private LocalDateTime readTime;
}