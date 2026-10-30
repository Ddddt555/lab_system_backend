package com.lab.labsystem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.Student;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {
    // 继承 BaseMapper 之后，自动就有了增删改查所有方法，啥都不用写！
    // 无视 @TableLogic，强行查数据库（用于判断主键是否被占）
    @Select("SELECT * FROM student WHERE student_no = #{studentNo}")
    Student selectRealById(String studentNo);
    
    // 用于复活被删除的数据
    @Update("UPDATE student SET deleted = 0 WHERE student_no = #{studentNo}")
    int restoreStudent(String studentNo);
    
    // 查询所有已删除的学生（回收站）
    @Select("SELECT * FROM student WHERE deleted = 1")
    List<Student> selectDeletedStudents();
}