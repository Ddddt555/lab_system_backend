package com.lab.labsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.SysNoticeRead;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeRead> {

    /**
     * 一键标记所有可见通知为已读
     * 利用 MySQL 的 INSERT IGNORE 特性，自动忽略已经存在的主键冲突（重复已读）
     * @return 受影响的行数，即本次新标记的未读数量
     */
    @Insert("<script>" +
            "INSERT IGNORE INTO sys_notice_read (notice_id, user_no, read_time) " +
            "SELECT n.notice_id, #{userNo}, NOW() FROM sys_notice n " +
            "WHERE n.deleted = 0 " +
            "<if test=\"role == 'student' or role == 'teacher'\">" +
            "AND (n.target_role = 'all' OR n.target_role = #{role}) " +
            "</if>" +
            "<if test=\"role == 'admin'\">" +
            "AND n.target_role = 'all' " +
            "</if>" +
            "</script>")
    int markAllRead(@Param("userNo") String userNo, @Param("role") String role);
}