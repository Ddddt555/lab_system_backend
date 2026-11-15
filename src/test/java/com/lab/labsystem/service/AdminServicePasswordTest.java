package com.lab.labsystem.service;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.service.admin.AdminService;
import com.lab.labsystem.service.auth.MustChangePasswordCache;
import com.lab.labsystem.service.auth.TokenService;

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
 */
class AdminServicePasswordTest {

    private SysAdminMapper adminMapper;
    private TokenService tokenService;
    private AdminService service;

    @BeforeEach
    void setUp() {
        adminMapper = mock(SysAdminMapper.class);
        tokenService = mock(TokenService.class);
        service = new AdminService();
        ReflectionTestUtils.setField(service, "adminMapper", adminMapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", mock(BCryptPasswordEncoder.class));
        ReflectionTestUtils.setField(service, "mustChangePasswordCache", mock(MustChangePasswordCache.class));
        ReflectionTestUtils.setField(service, "tokenService", tokenService);
    }

    @Test
    @DisplayName("弱密码在查库之前就被拒绝")
    void rejectWeakPassword() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updatePassword("old", "abc"));

        assertEquals("密码长度不能少于 8 位！", e.getMessage());
        verify(adminMapper, never()).selectOne(any());
        verify(tokenService, never()).revokeAllDevices(any());
    }
}