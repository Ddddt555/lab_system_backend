package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.dto.request.TeacherCreateDTO;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.auth.MustChangePasswordCache;
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
    
    @Test
    @DisplayName("新增教师：忽略前端传的密码，强制使用后端初始密码")
    void addTeacherIgnoresFrontendPassword() {
        // 1. 准备依赖 Mock
        NumberGenerator numberGenerator = mock(NumberGenerator.class);
        when(numberGenerator.generateWorkNo()).thenReturn("T2026001");
        ReflectionTestUtils.setField(service, "numberGenerator", numberGenerator);
        
        when(passwordEncoder.encode("123456")).thenReturn("encoded-default-pwd");
        when(teacherMapper.selectRealById("T2026001")).thenReturn(null);

        // 2. 模拟前端恶意传入 DTO（如果 DTO 里有 password 字段的话）
        // 注意：如果 DTO 里根本没有 password 字段，这里根本连 set 都调不了，这才是最安全的。
        TeacherCreateDTO dto = new TeacherCreateDTO();
        dto.setName("王老师");
        dto.setDeptNo("D001");
        // dto.setPassword("hacked-pwd"); // DTO 中不存在此字段，编译器会直接报错

        // 3. 调用
        String result = service.addTeacher(dto);

        // 4. 验证
        assertEquals("教师添加成功！工号为：T2026001", result);
        
        // 抓取插入的 Teacher 对象，验证密码和工号
        org.mockito.ArgumentCaptor<Teacher> captor = org.mockito.ArgumentCaptor.forClass(Teacher.class);
        verify(teacherMapper).insert(captor.capture());
        Teacher savedTeacher = captor.getValue();
        
        assertEquals("T2026001", savedTeacher.getWorkNo());
        assertEquals("encoded-default-pwd", savedTeacher.getPassword()); // 密码一定是后端生成的
        assertEquals(1, savedTeacher.getMustChangePassword()); // 必须强制改密
    }
}
