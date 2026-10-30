package com.lab.labsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.ExpSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExpSessionMapper extends BaseMapper<ExpSession> {
    // 什么方法都不用写，BaseMapper 自带的就够用了！
}