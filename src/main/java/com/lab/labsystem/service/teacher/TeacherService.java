package com.lab.labsystem.service.teacher;

import com.lab.labsystem.dto.request.TeacherCreateDTO;

public interface TeacherService {
    
    Object getTeacherList(Integer page, Integer size);
    
    // 修改为接收 DTO
    String addTeacher(TeacherCreateDTO dto);
    
    void deleteTeacher(String workNo);

    // 教师自助修改密码
    String changePassword(String workNo, String oldPwd, String newPwd);
}