package com.lab.labsystem.service.teacher;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface TeacherScoreService {
    // 录入平时成绩并计算总评
    String saveScore(String currentWorkNo, String studentNo, String classNo, Double dailyScore);
    
    // 导出成绩单
    void exportScores(String currentWorkNo, String classNo, HttpServletResponse response) throws IOException;
    
    // ⚠️ 新增：修改权重并重算全班成绩
    String updateWeight(String currentWorkNo, String classNo, Integer dailyWeight, Integer reportWeight);
    
    // ⚠️ 新增：当报告被批阅后，自动重算该学生的报告均分和总评
    void recalculateAfterGrade(String studentNo, String classNo);

    // 教学班成绩列表（含内存分页）
    Object getCourseScores(String currentWorkNo, String classNo, Integer page, Integer size);

    // 成绩分布统计（优/良/中/及格/不及格）
    java.util.Map<String, Integer> getScoreDistribution(String currentWorkNo);

    // 实验开出率统计
    java.util.Map<String, Object> getExperimentRate(String currentWorkNo);
}