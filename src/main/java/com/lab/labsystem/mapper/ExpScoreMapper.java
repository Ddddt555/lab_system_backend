package com.lab.labsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.ExpScore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExpScoreMapper extends BaseMapper<ExpScore> {
    // 🛑【核心修复】物理删除被逻辑删除的旧成绩记录，防止重复进入导致唯一键冲突
    @Delete("DELETE FROM exp_score WHERE student_no = #{studentNo} AND class_no = #{classNo} AND deleted = 1")
    void deleteOldSoftDeletedScores(@Param("studentNo") String studentNo, @Param("classNo") String classNo);
}