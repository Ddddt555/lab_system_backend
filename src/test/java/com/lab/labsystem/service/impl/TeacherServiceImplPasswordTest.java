package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.MustChangePasswordCache;
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
 * 教师自助改密的规则测试（与学生端保持同一套约束）。
 */
class TeacherServiceImplPasswordTest {

    private TeacherMapper teacherMapper;
    private BCryptPasswordEncoder passwordEncoder;
    private MustChangePasswordCache cache;
    private TeacherServiceImpl service;

    @BeforeEach
    void setUp() {
        teacherMapper = mock(TeacherMapper.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        cache = mock(MustChangePasswordCache.class);

        service = new TeacherServiceImpl();
        ReflectionTestUtils.setField(service, "teacherMapper", teacherMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "mustChangePasswordCache", cache);
    }

    @Test
    @DisplayName("弱密码在查库之前就被拒绝")
    void rejectWeakPassword() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.changePassword("T001", "old", "abc"));

        assertEquals("密码长度不能少于 8 位！", e.getMessage());
        verify(teacherMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("原密码不正确时拒绝改密")
    void rejectWrongOldPassword() {
        Teacher teacher = new Teacher();
        teacher.setWorkNo("T001");
        teacher.setPassword("stored-hash");
        when(teacherMapper.selectById("T001")).thenReturn(teacher);
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.changePassword("T001", "wrong", "Abc123@x"));

        assertEquals("原密码错误！", e.getMessage());
        verify(teacherMapper, never()).updateById(any(Teacher.class));
        verify(cache, never()).evict(any(), any());
    }

    @Test
    @DisplayName("改密成功后清除强制改密标记与缓存")
    void successClearsMustChangeAndCache() {
        Teacher teacher = new Teacher();
        teacher.setWorkNo("T001");
        teacher.setPassword("stored-hash");
        teacher.setMustChangePassword(1);
        when(teacherMapper.selectById("T001")).thenReturn(teacher);
        when(passwordEncoder.matches("old-pass", "stored-hash")).thenReturn(true);
        when(passwordEncoder.encode("Abc123@x")).thenReturn("new-hash");

        String msg = service.changePassword("T001", "old-pass", "Abc123@x");

        assertEquals("密码修改成功！", msg);
        assertEquals("new-hash", teacher.getPassword());
        assertEquals(0, teacher.getMustChangePassword());
        verify(teacherMapper).updateById(teacher);
        verify(cache).evict("teacher", "T001");
    }
}
