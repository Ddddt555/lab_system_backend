package com.lab.labsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.Teacher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TeacherMapper extends BaseMapper<Teacher> {
    // 无视 @TableLogic，强行查数据库（用于判断主键是否被占）
    @Select("SELECT * FROM teacher WHERE work_no = #{workNo}")
    Teacher selectRealById(String workNo);

    // 用于复活被删除的数据
    @Update("UPDATE teacher SET deleted = 0 WHERE work_no = #{workNo}")
    int restoreTeacher(String workNo);

    // 查询所有已删除的教师（回收站）
    @Select("SELECT * FROM teacher WHERE deleted = 1")
    List<Teacher> selectDeletedTeachers();
}