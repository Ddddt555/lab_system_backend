package com.lab.labsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.SysLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface SysLogMapper extends BaseMapper<SysLog> {

    /**
     * 物理删除指定时间之前的日志
     * 绕过 @TableLogic 的逻辑删除机制，直接清理磁盘空间
     */
    @Delete("DELETE FROM sys_log WHERE create_time < #{cutoff}")
    int physicalDeleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}