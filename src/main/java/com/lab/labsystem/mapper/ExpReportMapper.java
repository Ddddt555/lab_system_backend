package com.lab.labsystem.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.ExpReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select; 
import java.util.List; 

@Mapper
public interface ExpReportMapper extends BaseMapper<ExpReport> {
    
    // 🛑【核心修复】物理删除被逻辑删除的旧报告记录
    @Delete("DELETE FROM exp_report WHERE student_no = #{studentNo} AND project_no = #{projectNo} AND deleted = 1")
    void deleteOldSoftDeletedReports(@Param("studentNo") String studentNo, @Param("projectNo") String projectNo);
    
    // 🛑【新增：查询所有报告文件名，用于清理孤儿文件】
    @Select("SELECT file_url FROM exp_report WHERE file_url IS NOT NULL")
    List<String> selectAllFileUrls();
}