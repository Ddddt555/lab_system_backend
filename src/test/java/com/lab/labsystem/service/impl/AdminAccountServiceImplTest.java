package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.admin.impl.AdminAccountServiceImpl;
import com.lab.labsystem.service.auth.MustChangePasswordCache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台重置密码的规则测试。
 *
 * <p>重置密码是提权敏感操作：必须置位"下次登录强制改密"，
 * 并且同步失效强制改密缓存，否则被重置的账号在缓存过期前仍可免改密继续使用。
 */
class AdminAccountServiceImplTest {

    private StudentMapper studentMapper;
    private TeacherMapper teacherMapper;
    private BCryptPasswordEncoder passwordEncoder;
    private MustChangePasswordCache cache;
    private AdminAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        studentMapper = mock(StudentMapper.class);
        teacherMapper = mock(TeacherMapper.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        cache = mock(MustChangePasswordCache.class);

        service = new AdminAccountServiceImpl(studentMapper, teacherMapper, passwordEncoder, cache);
        when(passwordEncoder.encode("123456")).thenReturn("default-hash");
    }

    @Test
    @DisplayName("学生不存在时拒绝重置")
    void studentNotFound() {
        when(studentMapper.selectById("S404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.resetPassword("S404", "student"));

        assertEquals("学生不存在！", e.getMessage());
        verify(studentMapper, never()).updateById(any(Student.class));
        verify(cache, never()).evict(any(), any());
    }

    @Test
    @DisplayName("教师不存在时拒绝重置")
    void teacherNotFound() {
        when(teacherMapper.selectById("T404")).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.resetPassword("T404", "teacher"));

        assertEquals("教师不存在！", e.getMessage());
        verify(teacherMapper, never()).updateById(any(Teacher.class));
    }

    @Test
    @DisplayName("不支持的角色被拒绝")
    void unsupportedRole() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.resetPassword("X1", "admin"));

        assertEquals("不支持的角色！", e.getMessage());
        verify(studentMapper, never()).updateById(any(Student.class));
        verify(teacherMapper, never()).updateById(any(Teacher.class));
    }

    @Test
    @DisplayName("重置学生密码：置位强制改密并失效缓存")
    void resetStudent() {
        Student student = new Student();
        student.setStudentNo("S001");
        student.setMustChangePassword(0);
        when(studentMapper.selectById("S001")).thenReturn(student);

        String msg = service.resetPassword("S001", "student");

        assertEquals("default-hash", student.getPassword());
        assertEquals(1, student.getMustChangePassword());
        verify(studentMapper).updateById(student);
        verify(cache).evict("student", "S001");
        assertEquals("密码已重置为 123456，该用户下次登录必须修改密码！", msg);
    }

    @Test
    @DisplayName("重置教师密码：置位强制改密并失效缓存")
    void resetTeacher() {
        Teacher teacher = new Teacher();
        teacher.setWorkNo("T001");
        teacher.setMustChangePassword(0);
        when(teacherMapper.selectById("T001")).thenReturn(teacher);

        service.resetPassword("T001", "teacher");

        assertEquals("default-hash", teacher.getPassword());
        assertEquals(1, teacher.getMustChangePassword());
        verify(teacherMapper).updateById(teacher);
        verify(cache).evict("teacher", "T001");
    }
}
