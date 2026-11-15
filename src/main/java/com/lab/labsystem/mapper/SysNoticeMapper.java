package com.lab.labsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 统计当前用户可见且未读的通知总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM sys_notice n " +
            "LEFT JOIN sys_notice_read r ON n.notice_id = r.notice_id AND r.user_no = #{userNo} " +
            "WHERE n.deleted = 0 AND r.id IS NULL " +
            "<if test=\"role == 'student' or role == 'teacher'\">" +
            "AND (n.target_role = 'all' OR n.target_role = #{role}) " +
            "</if>" +
            "<if test=\"role == 'admin'\">" +
            "AND n.target_role = 'all' " +
            "</if>" +
            "</script>")
    long countUnread(@Param("userNo") String userNo, @Param("role") String role);
}