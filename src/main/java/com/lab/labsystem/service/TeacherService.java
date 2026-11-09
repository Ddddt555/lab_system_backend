package com.lab.labsystem.service;
import com.lab.labsystem.entity.Teacher;
public interface TeacherService {
    Object getTeacherList(Integer page, Integer size);
    String addTeacher(Teacher teacher);
    void deleteTeacher(String workNo);

    // 教师自助修改密码
    String changePassword(String workNo, String oldPwd, String newPwd);
}