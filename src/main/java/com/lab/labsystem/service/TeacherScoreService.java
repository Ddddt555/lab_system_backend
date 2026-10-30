package com.lab.labsystem.service;

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
}