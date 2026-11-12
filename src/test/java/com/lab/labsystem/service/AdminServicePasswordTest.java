package com.lab.labsystem.service;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.service.admin.AdminService;
import com.lab.labsystem.service.auth.MustChangePasswordCache;

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

/**
 * 管理员自助改密的校验规则测试。
 *
 * <p>密码强度校验从 controller 移到了 service，这里锁住"先校验、后查库"的顺序，
 * 避免弱口令请求打到数据库。
 */
class AdminServicePasswordTest {

    private SysAdminMapper adminMapper;
    private AdminService service;

    @BeforeEach
    void setUp() {
        adminMapper = mock(SysAdminMapper.class);
        service = new AdminService();
        ReflectionTestUtils.setField(service, "adminMapper", adminMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(BCryptPasswordEncoder.class));
        ReflectionTestUtils.setField(service, "mustChangePasswordCache", mock(MustChangePasswordCache.class));
    }

    @Test
    @DisplayName("弱密码在查库之前就被拒绝")
    void rejectWeakPassword() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updatePassword("old", "abc"));

        assertEquals("密码长度不能少于 8 位！", e.getMessage());
        verify(adminMapper, never()).selectOne(any());
    }
}
