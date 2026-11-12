package com.lab.labsystem.service.admin.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.common.ExcelUtils;
import com.lab.labsystem.dto.StudentImportDTO;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.admin.AdminAccountService;
import com.lab.labsystem.service.auth.MustChangePasswordCache;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    /** 重置后的初始密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MustChangePasswordCache mustChangePasswordCache;

    public AdminAccountServiceImpl(StudentMapper studentMapper,
                                   TeacherMapper teacherMapper,
                                   BCryptPasswordEncoder passwordEncoder,
                                   MustChangePasswordCache mustChangePasswordCache) {
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.passwordEncoder = passwordEncoder;
        this.mustChangePasswordCache = mustChangePasswordCache;
    }

    @Override
    public String resetPassword(String account, String role) {
        String defaultHash = passwordEncoder.encode(DEFAULT_PASSWORD);

        if ("student".equals(role)) {
            Student student = studentMapper.selectById(account);
            if (student == null) {
                throw BusinessException.of(ErrorCode.DATA_NOT_FOUND, "学生不存在！");
            }
            student.setPassword(defaultHash);
            student.setMustChangePassword(1);
            student.setPwdUpdateTime(LocalDateTime.now());
            studentMapper.updateById(student);
            // 缓存一致性：重置后必须立即失效，否则最多 60 秒内仍被判定为"无需改密"
            mustChangePasswordCache.evict("student", account);

        } else if ("teacher".equals(role)) {
            Teacher teacher = teacherMapper.selectById(account);
            if (teacher == null) {
                throw BusinessException.of(ErrorCode.DATA_NOT_FOUND, "教师不存在！");
            }
            teacher.setPassword(defaultHash);
            teacher.setMustChangePassword(1);
            teacher.setPwdUpdateTime(LocalDateTime.now());
            teacherMapper.updateById(teacher);
            mustChangePasswordCache.evict("teacher", account);

        } else {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "不支持的角色！");
        }

        return "密码已重置为 " + DEFAULT_PASSWORD + "，该用户下次登录必须修改密码！";
    }

    @Override
    public List<Student> listDeletedStudents() {
        return studentMapper.selectDeletedStudents();
    }

    @Override
    public String restoreStudent(String studentNo) {
        studentMapper.restoreStudent(studentNo);
        return "学生已成功恢复！";
    }

    @Override
    public List<Teacher> listDeletedTeachers() {
        return teacherMapper.selectDeletedTeachers();
    }

    @Override
    public String restoreTeacher(String workNo) {
        teacherMapper.restoreTeacher(workNo);
        return "教师已成功恢复！";
    }

    @Override
    public void exportStudentImportTemplate(HttpServletResponse response) throws IOException {
        ExcelUtils.export(response, "学生导入模板", StudentImportDTO.class, new ArrayList<>());
    }
}