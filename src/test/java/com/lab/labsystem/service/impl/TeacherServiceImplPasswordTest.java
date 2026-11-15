package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.dto.request.TeacherCreateDTO;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.auth.MustChangePasswordCache;
import com.lab.labsystem.service.auth.TokenService;
import com.lab.labsystem.service.teacher.impl.TeacherServiceImpl;

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

class TeacherServiceImplPasswordTest {

    private TeacherMapper teacherMapper;
    private BCryptPasswordEncoder passwordEncoder;
    private MustChangePasswordCache cache;
    private TokenService tokenService;
    private TeacherServiceImpl service;

    @BeforeEach
    void setUp() {
        teacherMapper = mock(TeacherMapper.class);
        passwordEncoder = mock(BCryptPasswordEncoder.class);
        cache = mock(MustChangePasswordCache.class);
        tokenService = mock(TokenService.class);

        service = new TeacherServiceImpl();
        ReflectionTestUtils.setField(service, "teacherMapper", teacherMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "mustChangePasswordCache", cache);
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
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
        verify(tokenService, never()).revokeAllDevices(any());
    }

    @Test
    @DisplayName("改密成功后清除强制改密标记、缓存并强退所有设备")
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
        verify(tokenService).revokeAllDevices("T001");
    }
    
    @Test
    @DisplayName("新增教师：忽略前端传的密码，强制使用后端初始密码")
    void addTeacherIgnoresFrontendPassword() {
        NumberGenerator numberGenerator = mock(NumberGenerator.class);
        when(numberGenerator.generateWorkNo()).thenReturn("T2026001");
        ReflectionTestUtils.setField(service, "numberGenerator", numberGenerator);
        
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default-pwd");
        when(teacherMapper.selectRealById("T2026001")).thenReturn(null);

        TeacherCreateDTO dto = new TeacherCreateDTO();
        dto.setName("王老师");
        dto.setDeptNo("D001");

        String result = service.addTeacher(dto);

        assertEquals("教师添加成功！工号为：T2026001", result);
        
        org.mockito.ArgumentCaptor<Teacher> captor = org.mockito.ArgumentCaptor.forClass(Teacher.class);
        verify(teacherMapper).insert(captor.capture());
        Teacher savedTeacher = captor.getValue();
        
        assertEquals("T2026001", savedTeacher.getWorkNo());
        assertEquals("encoded-default-pwd", savedTeacher.getPassword());
        assertEquals(1, savedTeacher.getMustChangePassword());
    }
}