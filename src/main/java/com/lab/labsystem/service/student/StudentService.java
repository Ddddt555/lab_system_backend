package com.lab.labsystem.service.student;

import com.lab.labsystem.dto.request.StudentCreateDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StudentService {
    
    // 分页/全量获取学生
    Object getStudentList(Integer page, Integer size);
    
    // 新增学生：修改为接收 DTO
    String addStudent(StudentCreateDTO dto);
    
    // 删除学生
    void deleteStudent(String studentNo);
    
    // 批量导入
    String importStudents(MultipartFile file) throws IOException;

    // 学生自助修改密码
    String changePassword(String studentNo, String oldPwd, String newPwd);
}