package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.service.auth.MustChangePasswordCache;
import com.lab.labsystem.service.student.impl.StudentServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学生自助改密的规则测试。
 *
 * <p>该类使用的是字段注入，这里用 ReflectionTestUtils 注入 mock，
 * 避免为了测试而整体改写这个已有类的构造方式。
 */
class StudentServiceImplPasswordTest {

    private StudentMapper studentMapper;
    private BCryptPasswordEncoder passwordEncoder;
    private MustChangePasswordCache cache;
    private StudentServiceImpl service;

    @BeforeEach
    void setUp() {
        studentMapper = mock(StudentMapper.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        cache = mock(MustChangePasswordCache.class);

        service = new StudentServiceImpl();
        ReflectionTestUtils.setField(service, "studentMapper", studentMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "mustChangePasswordCache", cache);
    }

    @Test
    @DisplayName("弱密码在查库之前就被拒绝")
    void rejectWeakPassword() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.changePassword("S001", "old", "abc"));

        assertEquals("密码长度不能少于 8 位！", e.getMessage());
        verify(studentMapper, never()).selectById(any());
        verify(studentMapper, never()).updateById(any(Student.class));
    }

    @Test
    @DisplayName("原密码不正确时拒绝改密")
    void rejectWrongOldPassword() {
        Student student = new Student();
        student.setStudentNo("S001");
        student.setPassword("stored-hash");
        when(studentMapper.selectById("S001")).thenReturn(student);
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.changePassword("S001", "wrong", "Abc123@x"));

        assertEquals("原密码错误！", e.getMessage());
        verify(studentMapper, never()).updateById(any(Student.class));
        verify(cache, never()).evict(any(), any());
    }

    @Test
    @DisplayName("改密成功后清除强制改密标记与缓存")
    void successClearsMustChangeAndCache() {
        Student student = new Student();
        student.setStudentNo("S001");
        student.setPassword("stored-hash");
        student.setMustChangePassword(1);
        when(studentMapper.selectById("S001")).thenReturn(student);
        when(passwordEncoder.matches("old-pass", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("Abc123@x")).thenReturn("new-hash");

        String msg = service.changePassword("S001", "old-pass", "Abc123@x");

        assertEquals("密码修改成功，请重新登录！", msg);
        assertEquals("new-hash", student.getPassword());
        assertEquals(0, student.getMustChangePassword());
        verify(studentMapper).updateById(student);
        // 缓存必须失效，否则旧状态会保留到 TTL 到期
        verify(cache).evict("student", "S001");
    }
}
