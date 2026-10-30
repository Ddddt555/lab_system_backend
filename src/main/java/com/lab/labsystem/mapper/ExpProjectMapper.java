package com.lab.labsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.ExpProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select; 
import java.util.List; 

@Mapper
public interface ExpProjectMapper extends BaseMapper<ExpProject> {
    
    //查询所有指导书文件名，用于清理孤儿文件
    @Select("SELECT guide_file FROM exp_project WHERE guide_file IS NOT NULL")
    List<String> selectAllGuideFiles();
}