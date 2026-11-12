package com.lab.labsystem.service.attendance;

import com.lab.labsystem.vo.StudentVO;
import java.util.List;

public interface AttendanceService {
    // 教师发起签到
    String startAttendance(String currentWorkNo, String sessionNo);
    
    // 教师获取签到名单
    List<StudentVO> getAttendanceList(String currentWorkNo, String sessionNo);
    
    // 学生签到
    String signIn(String studentNo, String sessionNo, String code);
    
    // 导出考勤表
    void exportAttendance(String currentWorkNo, String sessionNo, jakarta.servlet.http.HttpServletResponse response) throws Exception;
}