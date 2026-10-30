package com.lab.labsystem.service;

import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.entity.Student;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface StudentService {
    // 分页/全量获取学生
    Object getStudentList(Integer page, Integer size);
    
    // 新增学生
    String addStudent(Student student);
    
    // 删除学生
    void deleteStudent(String studentNo);
    
    // 批量导入
    String importStudents(MultipartFile file) throws IOException;
}